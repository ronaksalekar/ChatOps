package com.chatops.message;

import com.chatops.exception.ForbiddenException;
import com.chatops.exception.ResourceNotFoundException;
import com.chatops.message.dto.MessagePage;
import com.chatops.message.dto.MessageResponse;
import com.chatops.room.Room;
import com.chatops.room.RoomMemberRepository;
import com.chatops.room.RoomRepository;
import com.chatops.user.User;
import com.chatops.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for message history retrieval.
 *
 * <p>Uses cursor-based pagination for stable, efficient history loading
 * that is not affected by new messages being inserted at the top.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT     = 100;

    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    /**
     * Returns a cursor-paginated page of messages for a room.
     *
     * @param roomId    the room to query
     * @param userId    the requesting user's ID (must be a room member)
     * @param before    cursorId to paginate before (null = first page)
     * @param limit     max messages to return (must be between 1 and {@value MAX_LIMIT})
     * @return a {@link MessagePage} with messages newest-first
     * @throws ForbiddenException if the user is not a room member
     */
    @Transactional(readOnly = true)
    public MessagePage getRoomMessages(Long roomId, Long userId, Long before, Integer limit) {
        if (before != null && before < 1) {
            throw new IllegalArgumentException("before cursor must be positive");
        }
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of this room");
        }

        int pageSize = limit != null ? limit : DEFAULT_LIMIT;

        List<Message> messages = (before == null)
                ? messageRepository.findLatestRoomMessages(roomId, pageSize)
                : messageRepository.findRoomMessagesBefore(roomId, before, pageSize);

        List<MessageResponse> responses = messages.stream()
                .map(this::toResponse)
                .toList();

        Long nextCursor = responses.isEmpty()
                ? null
                : responses.get(responses.size() - 1).cursorId();

        // hasMore: check if there's at least one message before the last cursorId
        boolean hasMore = nextCursor != null &&
                !messageRepository.findRoomMessagesBefore(roomId, nextCursor, 1).isEmpty();

        return new MessagePage(responses, nextCursor, hasMore);
    }

    /**
     * Saves a new message to the database and returns its response DTO.
     * This overload is used by the WebSocket {@code ChatController} where
     * only the room ID, sender username, and text content are available.
     *
     * @param roomId   the target room
     * @param username the sender's username
     * @param content  the message text
     * @return persisted message as a response DTO
     */
    @Transactional
    public MessageResponse saveMessage(Long roomId, String username, String content) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .messageType(MessageType.TEXT)
                .build();

        return saveMessage(message);
    }

    /**
     * Saves a fully-constructed message entity and returns its response DTO.
     */
    @Transactional
    public MessageResponse saveMessage(Message message) {
        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    // ===== Helpers =====

    public MessageResponse toResponse(Message msg) {
        return new MessageResponse(
                msg.getId(),
                msg.getCursorId(),
                msg.getRoom() != null ? msg.getRoom().getId() : null,
                msg.getSender().getId(),
                msg.getSender().getUsername(),
                msg.getContent(),
                msg.getMessageType(),
                msg.getCreatedAt()
        );
    }
}
