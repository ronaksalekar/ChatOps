package com.chatops.room;

import com.chatops.room.dto.CreateRoomRequest;
import com.chatops.room.dto.RoomResponse;
import com.chatops.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for room management.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Create, list, join, and leave chat rooms")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Create a new room")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.createRoom(request, currentUser));
    }

    @GetMapping
    @Operation(summary = "List rooms the current user belongs to")
    public ResponseEntity<List<RoomResponse>> getMyRooms(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(roomService.getUserRooms(currentUser));
    }

    @GetMapping("/public")
    @Operation(summary = "List all public rooms available to join")
    public ResponseEntity<List<RoomResponse>> getPublicRooms() {
        return ResponseEntity.ok(roomService.getPublicRooms());
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get room details")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(roomService.getRoom(roomId, currentUser.getUsername()));
    }

    @PostMapping("/join")
    @Operation(summary = "Join a room using its unique invite code (public or private)")
    public ResponseEntity<RoomResponse> joinByInviteCode(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User currentUser
    ) {
        String inviteCode = body.get("inviteCode");
        if (inviteCode == null || inviteCode.isBlank()) {
            inviteCode = body.get("code");
        }
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("Invite code is required");
        }
        return ResponseEntity.ok(roomService.joinByInviteCode(inviteCode, currentUser));
    }

    @PostMapping("/{roomId}/invite")
    @Operation(summary = "Directly invite another user to this room by username")
    public ResponseEntity<RoomResponse> inviteUser(
            @PathVariable Long roomId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User currentUser
    ) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        return ResponseEntity.ok(roomService.inviteUser(roomId, username, currentUser));
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "Join a room")
    public ResponseEntity<Void> joinRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        roomService.joinRoom(roomId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/leave")
    @Operation(summary = "Leave a room")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        roomService.leaveRoom(roomId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "Delete a room (admins only)")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        roomService.deleteRoom(roomId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
