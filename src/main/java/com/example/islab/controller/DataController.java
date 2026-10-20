package com.example.islab.controller;

import com.example.islab.dto.UserDto;
import com.example.islab.entity.Post;
import com.example.islab.repository.PostRepository;
import com.example.islab.repository.UserRepository;
import com.example.islab.util.HtmlSanitizer;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")

public class DataController {
	private final UserRepository userRepository;
    private final PostRepository postRepository;

    public DataController(UserRepository userRepository, PostRepository postRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    /** GET /api/data — protected; returns list of users (no passwords). */
    @GetMapping("/data")
    public List<UserDto> getData() {
        return userRepository.findAll().stream()
                .map(u -> new UserDto(
                        u.getId(),
                        HtmlSanitizer.escape(u.getUsername())))
                .toList();
    }

    /** GET /api/posts — third endpoint; returns posts with escaped content. */
    @GetMapping("/posts")
    public List<PostView> getPosts() {
        return postRepository.findAll().stream()
                .map(p -> new PostView(
                        p.getId(),
                        HtmlSanitizer.escape(p.getTitle()),
                        HtmlSanitizer.escape(p.getBody()),
                        p.getAuthor() == null ? null : HtmlSanitizer.escape(p.getAuthor().getUsername())))
                .toList();
    }

    public record PostView(Long id, String title, String body, String author) {}
}
