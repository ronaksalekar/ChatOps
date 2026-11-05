package com.chatops.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Room} persistence.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Finds all rooms that a given user is a member of.
     */
    @Query("""
            SELECT r FROM Room r
            JOIN r.members m
            WHERE m.user.id = :userId
            ORDER BY r.updatedAt DESC
            """)
    List<Room> findRoomsByUserId(@Param("userId") Long userId);

    /**
     * Finds all public rooms.
     */
    List<Room> findByIsPrivateFalseOrderByUpdatedAtDesc();

    /**
     * Finds a room by its unique invite code.
     */
    Optional<Room> findByInviteCode(String inviteCode);

    /**
     * Finds a room by ID and eagerly loads its member list.
     */
    @Query("""
            SELECT r FROM Room r
            LEFT JOIN FETCH r.members m
            LEFT JOIN FETCH m.user
            WHERE r.id = :roomId
            """)
    Optional<Room> findByIdWithMembers(@Param("roomId") Long roomId);
}
