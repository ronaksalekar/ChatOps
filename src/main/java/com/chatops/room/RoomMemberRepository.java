package com.chatops.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link RoomMember} membership records.
 */
@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    long countByRoomIdAndRole(Long roomId, RoomRole role);

    /**
     * Returns the IDs of all rooms a user (identified by username) belongs to.
     * Used by {@link com.chatops.websocket.WebSocketEventListener} to broadcast
     * presence events to relevant rooms on connect/disconnect.
     */
    @Query("SELECT rm.room.id FROM RoomMember rm WHERE rm.user.username = :username")
    List<Long> findRoomIdsByUsername(@Param("username") String username);

    /**
     * Returns the usernames of all members of a given room.
     * Used by the presence endpoint to bulk-query online status.
     */
    @Query("SELECT rm.user.username FROM RoomMember rm WHERE rm.room.id = :roomId")
    List<String> findUsernamesByRoomId(@Param("roomId") Long roomId);

    /**
     * Checks membership by username (bypasses a user-entity lookup).
     */
    @Query("SELECT COUNT(rm) > 0 FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.user.username = :username")
    boolean existsByRoomIdAndUsername(@Param("roomId") Long roomId, @Param("username") String username);
}
