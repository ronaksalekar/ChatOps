package com.chatops.message;

import com.chatops.message.dto.MessagePage;
import com.chatops.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for cursor-paginated message history.
 *
 * <p>To load the first page, omit the {@code before} parameter.
 * For subsequent pages, pass the {@code nextCursor} from the previous response.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Cursor-paginated message history for rooms")
public class MessageController {

    private final MessageService messageService;

    /**
     * Returns paginated message history for a room.
     *
     * @param roomId      the room to fetch messages for
     * @param before      cursorId for pagination (omit for first page)
     * @param limit       number of messages to return (default 50, max 100)
     * @param currentUser the authenticated requesting user
     * @return a page of messages, newest-first
     */
    @GetMapping
    @Operation(summary = "Get cursor-paginated message history for a room")
    public ResponseEntity<MessagePage> getMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User currentUser
    ) {
        MessagePage page = messageService.getRoomMessages(
                roomId, currentUser.getId(), before, limit);
        return ResponseEntity.ok(page);
    }
}
