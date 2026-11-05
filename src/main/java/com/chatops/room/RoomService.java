package com.chatops.room;

import com.chatops.exception.ConflictException;
import com.chatops.exception.ForbiddenException;
import com.chatops.exception.ResourceNotFoundException;
import com.chatops.room.dto.CreateRoomRequest;
import com.chatops.room.dto.RoomResponse;
import com.chatops.user.User;
import com.chatops.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for room lifecycle and membership management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new room and automatically adds the creator as ADMIN.
     */
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, User creator) {
        Room room = Room.builder()
                .name(request.name())
                .description(request.description())
                .createdBy(creator)
                .isPrivate(request.isPrivate())
                .build();

        room = roomRepository.save(room);

        // Creator is automatically an ADMIN member
        RoomMember membership = RoomMember.builder()
                .id(new RoomMemberId(room.getId(), creator.getId()))
                .room(room)
                .user(creator)
                .role(RoomRole.ADMIN)
                .build();
        roomMemberRepository.save(membership);

        log.info("Room '{}' created by {}", room.getName(), creator.getUsername());
        return toResponse(room, 1L);
    }

    /**
     * Returns all rooms the given user is a member of.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> getUserRooms(User user) {
        return roomRepository.findRoomsByUserId(user.getId()).stream()
                .map(r -> toResponse(r, roomMemberRepository.countByRoomId(r.getId())))
                .toList();
    }

    /**
     * Returns all public rooms.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> getPublicRooms() {
        return roomRepository.findByIsPrivateFalseOrderByUpdatedAtDesc().stream()
                .map(r -> toResponse(r, roomMemberRepository.countByRoomId(r.getId())))
                .toList();
    }

    /**
     * Returns details of a specific room, asserting the caller is a member.
     *
     * @throws ResourceNotFoundException if the room does not exist
     * @throws ForbiddenException        if the caller is not a member
     */
    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, String callerUsername) {
        if (!roomMemberRepository.existsByRoomIdAndUsername(roomId, callerUsername)) {
            throw new ForbiddenException("You are not a member of this room");
        }
        Room room = getRoomOrThrow(roomId);
        return toResponse(room, roomMemberRepository.countByRoomId(roomId));
    }

    /**
     * Adds the requesting user to the room.
     *
     * @throws ConflictException if the user is already a member
     */
    @Transactional
    public void joinRoom(Long roomId, User user) {
        Room room = getRoomOrThrow(roomId);

        if (room.isPrivate()) {
            throw new ForbiddenException("This channel is private. Please join using its Invite Code.");
        }

        if (roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            throw new ConflictException("You are already a member of this room");
        }

        RoomMember membership = RoomMember.builder()
                .id(new RoomMemberId(roomId, user.getId()))
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .build();
        roomMemberRepository.save(membership);

        log.info("User {} joined room {}", user.getUsername(), room.getName());
    }

    /**
     * Adds the requesting user to a room using its unique invite code.
     * Works for both public and private rooms.
     */
    @Transactional
    public RoomResponse joinByInviteCode(String inviteCode, User user) {
        Room room = roomRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No channel found with invite code: " + inviteCode));

        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
            return toResponse(room, roomMemberRepository.countByRoomId(room.getId()));
        }

        RoomMember membership = RoomMember.builder()
                .id(new RoomMemberId(room.getId(), user.getId()))
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .build();
        roomMemberRepository.save(membership);

        log.info("User {} joined room '{}' via invite code", user.getUsername(), room.getName());
        return toResponse(room, roomMemberRepository.countByRoomId(room.getId()));
    }

    /**
     * Directly invites/adds another user to the room by username.
     */
    @Transactional
    public RoomResponse inviteUser(Long roomId, String targetUsername, User caller) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, caller.getId())) {
            throw new ForbiddenException("You must be a member of this channel to invite others");
        }

        User targetUser = userRepository.findByUsername(targetUsername.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUsername));

        if (roomMemberRepository.existsByRoomIdAndUserId(roomId, targetUser.getId())) {
            throw new ConflictException("User '" + targetUsername + "' is already a member of this channel");
        }

        Room room = getRoomOrThrow(roomId);
        RoomMember membership = RoomMember.builder()
                .id(new RoomMemberId(roomId, targetUser.getId()))
                .room(room)
                .user(targetUser)
                .role(RoomRole.MEMBER)
                .build();
        roomMemberRepository.save(membership);

        log.info("User {} invited {} to room '{}'", caller.getUsername(), targetUsername, room.getName());
        return toResponse(room, roomMemberRepository.countByRoomId(roomId));
    }

    /**
     * Removes the requesting user from the room.
     *
     * @throws ForbiddenException        if the user is not a member
     * @throws ConflictException         if the user is the sole admin
     */
    @Transactional
    public void leaveRoom(Long roomId, User user) {
        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this room"));

        if (membership.getRole() == RoomRole.ADMIN
                && roomMemberRepository.countByRoomIdAndRole(roomId, RoomRole.ADMIN) <= 1) {
            long totalMembers = roomMemberRepository.countByRoomId(roomId);
            if (totalMembers <= 1) {
                Room room = getRoomOrThrow(roomId);
                roomRepository.delete(room);
                log.info("Sole member and admin {} left room {}, room deleted", user.getUsername(), roomId);
                return;
            }
            throw new ConflictException("The last room admin cannot leave while other members are present. You can delete the channel instead.");
        }

        roomMemberRepository.deleteByRoomIdAndUserId(roomId, user.getId());
        log.info("User {} left room {}", user.getUsername(), roomId);
    }

    /**
     * Deletes the room and cascades to all its members and messages.
     * Only callable by a room ADMIN.
     */
    @Transactional
    public void deleteRoom(Long roomId, User user) {
        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this room"));

        if (membership.getRole() != RoomRole.ADMIN) {
            throw new ForbiddenException("Only room admins can delete this room");
        }

        Room room = getRoomOrThrow(roomId);
        roomRepository.delete(room);
        log.info("Room '{}' (id: {}) deleted by admin {}", room.getName(), roomId, user.getUsername());
    }

    /**
     * Checks whether a user (by ID) is a member of the given room.
     * Used by WebSocket message controllers for authorization.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long roomId, Long userId) {
        return roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);
    }

    /**
     * Checks whether a user (by username) is a member of the given room.
     * Used by WebSocket controllers where only the principal name is available.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long roomId, String username) {
        return roomMemberRepository.existsByRoomIdAndUsername(roomId, username);
    }

    /**
     * Returns the usernames of all members of a given room.
     * Used by the presence endpoint for bulk online-status queries.
     */
    @Transactional(readOnly = true)
    public List<String> getRoomMemberUsernames(Long roomId) {
        return roomMemberRepository.findUsernamesByRoomId(roomId);
    }

    // ===== Helpers =====

    private Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    }

    private RoomResponse toResponse(Room room, long memberCount) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getCreatedBy() != null ? room.getCreatedBy().getId() : null,
                room.getCreatedBy() != null ? room.getCreatedBy().getUsername() : null,
                room.isPrivate(),
                room.getInviteCode(),
                memberCount,
                room.getCreatedAt()
        );
    }
}
