package com.chatops.message.dto;

import java.util.List;

/**
 * Cursor-paginated page of messages.
 *
 * <p>To fetch the next page, pass {@code nextCursor} as the {@code before} query parameter.
 * When {@code hasMore} is {@code false}, there are no earlier messages.
 *
 * @param messages   the messages for this page (ordered newest-first)
 * @param nextCursor the cursorId to use as {@code before=} on the next request, or null
 * @param hasMore    whether more messages exist before this page
 */
public record MessagePage(
        List<MessageResponse> messages,
        Long nextCursor,
        boolean hasMore
) {}
