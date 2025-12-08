package com.vani.week4.backend.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author vani
 * @since 10/14/25
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_contents")
public class PostContent {
    @Id
    @Column(name = "post_id")
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "post_id")
    private Post post;

    @Lob                        //longtext
    @Column(nullable = false,  columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "post_image_key")
    private String postImageKey;

    @Builder
    private PostContent(Post post, String content, String postImageKey) {
        this.id = post.getId();
        this.post = post;
        this.content = content;
        this.postImageKey = postImageKey;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updatePostImageKey(String postImageKey) {
        this.postImageKey = postImageKey;
    }
}
