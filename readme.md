# ISLab1 — Spring Boot + PostgreSQL

A small REST API that demonstrates **three required security controls end-to-end**:

1. **SQL‑injection protection** — all database access goes through Spring Data JPA
   repositories, which emit **parameterized prepared statements**. No query is ever
   assembled by string concatenation.
2. **XSS protection** — every user‑supplied string returned by the API is HTML‑escaped
   with `org.springframework.web.util.HtmlUtils.htmlEscape(...)` before it leaves the
   controller.
3. **Broken‑authentication protection** — passwords are stored as **BCrypt** hashes,
   successful login returns a **JWT**, and a Spring Security filter chain requires a
   valid `Authorization: Bearer <token>` header on every protected endpoint.

The project also ships a **GitHub Actions** pipeline that runs **SAST** (SpotBugs)
and **SCA** (OWASP Dependency‑Check) on every push and pull request.


---

## Architecture

```
Client ──HTTP──► JwtAuthFilter ──► SecurityConfig rules ──► Controller ──► Repository ──► PostgreSQL
                       │
                       └─ validates JWT, populates SecurityContext
```

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 16 (Docker Compose) |
| Auth | Spring Security + jjwt 0.12 |
| Hashing | BCrypt (`BCryptPasswordEncoder`) |
| Build | Maven |
| CI | GitHub Actions |
| SAST | SpotBugs Maven plugin |
| SCA | OWASP Dependency‑Check action |

---

## API reference

Base URL: `http://localhost:8080`

### 1. `POST /auth/login` — obtain a JWT

**Request**

```http
POST /auth/login
Content-Type: application/json

{
  "login": "alice",
  "password": "password123"
}
```

**Success `200`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "expiresInMs": 3600000
}
```

**Failure `403`** — invalid credentials, or any SQLi payload (see [Security measures](#sql-injection-protection)).

---

### 2. `GET /api/data` — list users (protected)

Requires `Authorization: Bearer <token>`.

**Request**

```http
GET /api/data
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Success `200`**

```json
[
  { "id": 1, "username": "alice", "email": "alice@example.com" },
  { "id": 2, "username": "bob",   "email": "bob@example.com" }
]
```

Passwords are never serialized. Usernames/emails are HTML‑escaped.

**Without token** — `403 Forbidden`.

---

### 3. `GET /api/posts` — list blog posts (protected)

Requires `Authorization: Bearer <token>`.

**Request**

```http
GET /api/posts
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Success `200`**

```json
[
  {
    "id": 1,
    "title": "Hello",
    "body": "&lt;script&gt;alert('xss')&lt;/script&gt; is neutralized",
    "author": "alice"
  }
]
```

Note the escaped `<script>` payload — this endpoint exists specifically to prove the
XSS control in action.

**Without token** — `403 Forbidden`.

---

## Security measures 

### SQL‑injection protection

**Approach:** every DB interaction is expressed as a Spring Data JPA repository method
or, where needed, a `@Query` with **named parameters**. Spring Data translates these
into JDBC prepared statements — the driver sends the SQL template and the user input
as *separate* wire messages, so input can never be interpreted as SQL.

**Repository layer** — no string concatenation anywhere:

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);   // → SELECT ... WHERE username = ?
}
```

**Authentication path** — user input reaches the DB through the same repository:

```java
// SecurityConfig#userDetailsService
return username -> userRepository.findByUsername(username) // parameterized
        .map(u -> User.withUsername(u.getUsername())
                      .password(u.getPassword())
                      .roles("USER").build())
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
```

**Proof** — sending `' OR '1'='1` as the login field:

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"alice'"'"' OR '"'"'1'"'"'='"'"'1","password":"x"}'
```

returns `403` — the driver treats the entire string as a literal username, finds no
matching row, and authentication fails without leaking any data.

**Verification in code review:** search the repository for `createQuery(`, `createNativeQuery(`
and `" + ` — none of them appear in the data‑access path.

---

### XSS protection

**Approach:** user‑supplied strings are HTML‑escaped *on the way out*, right before
serialization, using Spring's built‑in `HtmlUtils`.

**Helper** — `src/main/java/com/example/secureapi/util/HtmlSanitizer.java`:

```java
public static String escape(String s) {
    return s == null ? null : HtmlUtils.htmlEscape(s);
}
```

`HtmlUtils.htmlEscape` converts `< > & " '` into `&lt; &gt; &amp; &quot; &#39;`. Any
stored `<script>` payload therefore arrives at the browser as inert text.

**Applied at the controller boundary** — `DataController`:

```java
@GetMapping("/data")
public List<UserDto> getData() {
    return userRepository.findAll().stream()
        .map(u -> new UserDto(
                u.getId(),
                HtmlSanitizer.escape(u.getUsername())))
        .toList();
}

@GetMapping("/posts")
public List<PostView> getPosts() {
    return postRepository.findAll().stream()
        .map(p -> new PostView(
                p.getId(),
                HtmlSanitizer.escape(p.getTitle()),      // ← XSS escape
                HtmlSanitizer.escape(p.getBody()),       // ← XSS escape
                p.getAuthor() == null ? null
                                      : HtmlSanitizer.escape(p.getAuthor().getUsername())))
        .toList();
}
```

**Proof** — the seed data contains a post whose body is
`<script>alert('xss')</script> is neutralized`. Calling `/api/posts` returns:

```json
"body": "&lt;script&gt;alert('xss')&lt;/script&gt; is neutralized"
```

**Note:** escaping is applied to *output*, not input, because the same data may be
consumed by non‑HTML clients (mobile apps, other services). If this API were also
serving raw HTML, a strict CSP and a DOM‑level sanitizer (e.g. OWASP Java HTML
Sanitizer) would be layered on top.

---

### Authentication (JWT + BCrypt)

**Password storage — BCrypt**

`BCryptPasswordEncoder` is the single `PasswordEncoder` bean:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

The seed runner stores only the hash:

```java
users.save(new User("alice", encoder.encode("password123"), "alice@example.com"));
```

`users.password` column therefore contains a `$2a$10$...` string — never plaintext.
`BCryptPasswordEncoder` uses a per‑password random salt and a configurable work
factor (default 10), so hashes cannot be rainbow‑table attacked and verification is
inherently constant‑time.

**Login flow — `POST /auth/login`**

```java
Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.login(), req.password()));
String token = jwtService.generateToken(auth.getName());
```

`AuthenticationManager` loads the user via `UserDetailsService` (parameterized
`findByUsername`), then calls `passwordEncoder.matches(rawPassword, storedHash)`.
On success a JWT is issued.

**Token issuance — `JwtService`**

```java
public String generateToken(String username) {
    Date now = new Date();
    return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(key)                       // HMAC‑SHA256, 32+ byte key
            .compact();
}
```

- Algorithm: **HS256** (`Keys.hmacShaKeyFor(secret.getBytes())`).
- Secret: read from env var `JWT_SECRET`, with a dev default for local runs.
- Lifetime: 1 hour (`app.jwt.expiration-ms=3600000`).

**Middleware — `JwtAuthFilter`**

Registered as a `OncePerRequestFilter` and inserted *before*
`UsernamePasswordAuthenticationFilter`:

```java
String header = request.getHeader("Authorization");
if (header == null || !header.startsWith("Bearer ")) {
    chain.doFilter(request, response);      // anonymous → 403 by SecurityConfig
    return;
}
String token = header.substring(7);
if (!jwtService.isValid(token)) {           // signature + expiry check
    chain.doFilter(request, response);
    return;
}
String username = jwtService.extractUsername(token);
UserDetails user = userDetailsService.loadUserByUsername(username);
var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
SecurityContextHolder.getContext().setAuthentication(auth);
chain.doFilter(request, response);
```

`isValid` catches `JwtException` and `IllegalArgumentException`, so tampered,
malformed, or expired tokens are silently ignored and the request proceeds
unauthenticated → blocked by `SecurityConfig`.

**Route protection — `SecurityConfig`**

```java
http.csrf(csrf -> csrf.disable())                          // stateless JWT API
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/login", "/error").permitAll()
        .anyRequest().authenticated())                    // everything else locked
    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

- `SessionCreationPolicy.STATELESS` — no server‑side session, so no session‑fixation risk.
- CSRF disabled deliberately: no cookies, and the token lives in an explicit
  `Authorization` header, which is not sent automatically by browsers.


SAST screenshots:
![SAST](docs/screenshots/005.png)

CVE: CVE-2018-8715
Description: By sending request with only an username but no password, the attacker can make access to the website without an account.

Reproduce the vulnerability:
![reproduce 01](docs/screenshots/001.png)
![reproduce 02](docs/screenshots/002.png)
Root cause analysis: Appweb give the wrong reponse when receiving a request with an username and no password, which is 200 OK and a cookie instead of 401 Unauthorized

Description of the applied fix: Update appweb to 7.0.3 or higher version, which changed the way of response when receiving a request containing a user name and no password.

Vulnerability elimination:
![fix 01](docs/screenshots/003.png)
![fix 02](docs/screenshots/004.png)