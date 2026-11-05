package com.chatops.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Message} persistence and cursor-paginated queries.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Fetches room messages before a given cursor (exclusive), newest-first.
     * Used for cursor-based pagination: pass the lowest cursorId from the
     * previous page as {@code beforeCursor}.
     *
     * @param roomId       the room to fetch messages for
     * @param beforeCursor only return messages with cursor_id strictly less than this value
     * @param limit        maximum number of messages to return
     * @return list ordered by cursor_id DESC (newest first)
     */
    @Query(value = """
            SELECT * FROM messages
            WHERE room_id = :roomId
              AND cursor_id < :beforeCursor
            ORDER BY cursor_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Message> findRoomMessagesBefore(
            @Param("roomId") Long roomId,
            @Param("beforeCursor") Long beforeCursor,
            @Param("limit") int limit
    );

    /**
     * Fetches the most recent messages in a room (first page, no cursor).
     */
    @Query(value = """
            SELECT * FROM messages
            WHERE room_id = :roomId
            ORDER BY cursor_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Message> findLatestRoomMessages(
            @Param("roomId") Long roomId,
            @Param("limit") int limit
    );
}
