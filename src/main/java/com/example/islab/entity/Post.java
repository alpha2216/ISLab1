package com.example.islab.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "posts")
public class Post {
	 @Id
	 @GeneratedValue(strategy = GenerationType.IDENTITY)
	 private Long id;

	 @Column(nullable = false, length = 200)
	 private String title;

	 @Column(nullable = false, length = 2000)
	 private String body;

	 @ManyToOne(fetch = FetchType.LAZY)
	 @JoinColumn(name = "author_id")
	 private User author;

	 public Post() {}
	 public Post(String title, String body, User author) {
		 this.title = title;
	     this.body = body;
	     this.author = author;
	 }

	 public Long getId() { 
		 return id; 
	 }
	 public String getTitle() { 
		 return title; 
	 }
	 public void setTitle(String title) { 
		 this.title = title; 
	 }
	 public String getBody() { return body; }
	 public void setBody(String body) { 
		 this.body = body; 
	 }
	 public User getAuthor() { 
		 return author; 
	 }
	 public void setAuthor(User author) { 
		 this.author = author; 
	 }
}
