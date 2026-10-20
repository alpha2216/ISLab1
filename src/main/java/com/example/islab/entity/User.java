package com.example.islab.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "users")
public class User {
	 @Id
	 @GeneratedValue(strategy = GenerationType.IDENTITY)
	 private Long id;

	 @Column(unique = true, nullable = false, length = 100)
	 private String username;

	 @Column(nullable = false, length = 100)
	 private String password;
	 
	 @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
	    private List<Post> posts = new ArrayList<>();
	 
	 public User() {}
	 public User(String username, String password) {
		 this.username = username;
	     this.password = password;
	 }
	 
	 public Long getId() {
		 return id;
	 }
	 public String getUsername() {
		 return username;
	 }
	 public void setUsername(String username) { 
		 this.username = username; 
	 }
	 public String getPassword() { 
		 return password; 
	 }
	 public void setPassword(String password) { 
		 this.password = password; 
	 }
	 
	 public List<Post> getPosts() {
		 return posts; 
	 }

	 public void addPost(Post post) {
	     posts.add(post);
	     post.setAuthor(this);
	 }

	 public void removePost(Post post) {
	     posts.remove(post);
	     post.setAuthor(null);
	 }
}
