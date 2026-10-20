package com.example.islab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.islab.entity.Post;
import com.example.islab.entity.User;
import com.example.islab.repository.PostRepository;
import com.example.islab.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class IsLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(IsLabApplication.class, args);
	}
	
	@Bean
    CommandLineRunner seed(UserRepository users, PostRepository posts, PasswordEncoder encoder) {
        return args -> {
            if (users.count() == 0) {
                User alice = users.save(new User("alice", encoder.encode("password123")));
                User bob   = users.save(new User("bob",   encoder.encode("password123")));
                posts.save(new Post("Hello", "<script>alert('xss')</script> is neutralized", alice));
                posts.save(new Post("Second", "Normal content", bob));
            }
        };
    }

}
