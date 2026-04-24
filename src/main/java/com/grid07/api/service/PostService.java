package com.grid07.api.service;

import com.grid07.api.dto.CommentDto;
import com.grid07.api.dto.PostDto;
import com.grid07.api.entity.Comment;
import com.grid07.api.entity.Post;
import com.grid07.api.entity.PostLike;
import com.grid07.api.repository.BotRepository;
import com.grid07.api.repository.CommentRepository;
import com.grid07.api.repository.PostLikeRepository;
import com.grid07.api.repository.PostRepository;
import com.grid07.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository       postRepository;
    private final CommentRepository    commentRepository;
    private final PostLikeRepository   postLikeRepository;
    private final UserRepository       userRepository;
    private final BotRepository        botRepository;
    private final GuardrailService     guardrailService;
    private final ViralityService      viralityService;
    private final NotificationService  notificationService;

    // -----------------------------------------------------------------------
    // Phase 1 – Create Post
    // -----------------------------------------------------------------------

    @Transactional
    public PostDto.Response createPost(PostDto.CreateRequest req) {
        validateAuthor(req.getAuthorId(), req.getAuthorType());

        Post post = Post.builder()
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .build();

        post = postRepository.save(post);
        log.info("[Post] Created postId={} by {}:{}", post.getId(), req.getAuthorType(), req.getAuthorId());
        return toResponse(post);
    }

    // -----------------------------------------------------------------------
    // Phase 1 + Phase 2 – Add Comment (with guardrails for bots)
    // -----------------------------------------------------------------------

    @Transactional
    public CommentDto.Response addComment(Long postId, CommentDto.CreateRequest req) {
        // Verify post exists
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + postId));

        validateAuthor(req.getAuthorId(), req.getAuthorType());

        boolean isBot = req.getAuthorType() == Comment.AuthorType.BOT;

        if (isBot) {
            applyBotGuardrails(postId, req);
        }

        // Persist comment to DB — only after all Redis guardrails pass
        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .depthLevel(req.getDepthLevel())
                .build();

        comment = commentRepository.save(comment);

        // Update virality score
        if (isBot) {
            viralityService.incrementForBotReply(postId);
            // Trigger notification engine if a post owner is known
            if (req.getPostOwnerId() != null) {
                String botName = botRepository.findById(req.getAuthorId())
                        .map(b -> b.getName())
                        .orElse("Bot#" + req.getAuthorId());
                notificationService.handleBotInteraction(req.getPostOwnerId(), botName, postId);
            }
        } else {
            viralityService.incrementForHumanComment(postId);
        }

        log.info("[Comment] Added commentId={} to postId={} by {}:{}",
                comment.getId(), postId, req.getAuthorType(), req.getAuthorId());
        return toCommentResponse(comment);
    }

    // -----------------------------------------------------------------------
    // Phase 1 + Phase 2 – Like Post
    // -----------------------------------------------------------------------

    @Transactional
    public void likePost(Long postId, Long userId) {
        // Verify post exists
        postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + postId));

        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        // Idempotency — don't double-like
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already liked this post");
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build();
        postLikeRepository.save(like);

        viralityService.incrementForHumanLike(postId);
        log.info("[Like] userId={} liked postId={}", userId, postId);
    }

    // -----------------------------------------------------------------------
    // Guardrail orchestration for bot comments
    // -----------------------------------------------------------------------

    /**
     * Enforces all three Phase-2 guardrails. Throws 429 / 400 on violation.
     * The horizontal cap uses an atomic Lua script; if it succeeds but the DB
     * write later fails, GuardrailService.decrementBotCount is called to roll back.
     */
    private void applyBotGuardrails(Long postId, CommentDto.CreateRequest req) {
        // 1. Vertical cap
        if (!guardrailService.checkVerticalCap(req.getDepthLevel())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vertical cap exceeded: thread depth cannot exceed 20 levels");
        }

        // 2. Cooldown cap (requires postOwnerId)
        if (req.getPostOwnerId() != null) {
            if (!guardrailService.tryAcquireCooldown(req.getAuthorId(), req.getPostOwnerId())) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Cooldown active: bot cannot interact with this user again within 10 minutes");
            }
        }

        // 3. Horizontal cap — must be LAST so we only increment if other checks pass
        if (!guardrailService.tryIncrementBotCount(postId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Horizontal cap exceeded: post has reached the maximum of 100 bot replies");
        }
    }

    // -----------------------------------------------------------------------
    // Validation helpers
    // -----------------------------------------------------------------------

    private void validateAuthor(Long authorId, Post.AuthorType authorType) {
        if (authorType == Post.AuthorType.USER) {
            userRepository.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + authorId));
        } else {
            botRepository.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found: " + authorId));
        }
    }

    private void validateAuthor(Long authorId, Comment.AuthorType authorType) {
        if (authorType == Comment.AuthorType.USER) {
            userRepository.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + authorId));
        } else {
            botRepository.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found: " + authorId));
        }
    }

    // -----------------------------------------------------------------------
    // Mappers
    // -----------------------------------------------------------------------

    private PostDto.Response toResponse(Post post) {
        PostDto.Response res = new PostDto.Response();
        res.setId(post.getId());
        res.setAuthorId(post.getAuthorId());
        res.setAuthorType(post.getAuthorType());
        res.setContent(post.getContent());
        res.setCreatedAt(post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);
        res.setViralityScore(viralityService.getScore(post.getId()));
        return res;
    }

    private CommentDto.Response toCommentResponse(Comment comment) {
        CommentDto.Response res = new CommentDto.Response();
        res.setId(comment.getId());
        res.setPostId(comment.getPostId());
        res.setAuthorId(comment.getAuthorId());
        res.setAuthorType(comment.getAuthorType());
        res.setContent(comment.getContent());
        res.setDepthLevel(comment.getDepthLevel());
        res.setCreatedAt(comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null);
        return res;
    }
}
