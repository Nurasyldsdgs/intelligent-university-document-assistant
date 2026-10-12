package com.university.docassistant.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted text into overlapping chunks for retrieval (RAG).
 * A chunk ends at the best natural boundary between {@link #MIN_CHUNK_SIZE} and {@link #MAX_CHUNK_SIZE} characters:
 * a paragraph break, else a sentence end, else a line break, else whitespace, and only as a last resort mid-word.
 * The next chunk starts about {@link #OVERLAP} characters earlier, at a word start, so a fact on a boundary
 * appears whole in at least one chunk. {@code content} is always exactly {@code text.substring(start, end)}.
 */
@Component
public class DocumentChunker {

    public static final int MAX_CHUNK_SIZE = 1_000;
    public static final int MIN_CHUNK_SIZE = 600;
    public static final int OVERLAP = 150;
    static final int PREVIEW_LENGTH = 120;

    /** One piece of the text; offsets are in characters of the full extracted text, end exclusive. */
    public record Chunk(int index, int startOffset, int endOffset, String content) {
        public String preview() {
            return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
        }
    }

    public List<Chunk> split(String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        int length = text.length();
        int start = skipWhitespace(text, 0, length);
        while (start < length) {
            int end = start + MAX_CHUNK_SIZE >= length ? length : boundary(text, start);
            int trimmedEnd = end;
            while (trimmedEnd > start && Character.isWhitespace(text.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }
            chunks.add(new Chunk(chunks.size(), start, trimmedEnd, text.substring(start, trimmedEnd)));
            if (end >= length) {
                break;
            }
            start = skipWhitespace(text, nextStart(text, start, end), length);
        }
        return chunks;
    }

    /** End of a chunk starting at {@code start} when the rest of the text is longer than a chunk. */
    private static int boundary(String text, int start) {
        int from = start + MIN_CHUNK_SIZE;
        int to = start + MAX_CHUNK_SIZE; // Exclusive end of the window, always < text.length() here.
        int end = lastIndexAfter(text, "\n\n", from, to);
        if (end < 0) {
            end = lastSentenceEnd(text, from, to);
        }
        if (end < 0) {
            end = lastIndexAfter(text, "\n", from, to);
        }
        if (end < 0) {
            end = lastIndexAfter(text, " ", from, to);
        }
        if (end < 0) {
            end = to;
            if (Character.isHighSurrogate(text.charAt(end - 1))) {
                end--; // Never split a surrogate pair.
            }
        }
        return end;
    }

    /** Start of the next chunk: about OVERLAP characters before {@code end}, moved to a word start. */
    private static int nextStart(String text, int start, int end) {
        int candidate = Math.max(end - OVERLAP, start + 1);
        if (candidate > start + 1 && !Character.isWhitespace(text.charAt(candidate - 1))) {
            int space = indexOfWhitespace(text, candidate, end);
            candidate = space < 0 ? candidate : space + 1;
        }
        if (Character.isLowSurrogate(text.charAt(candidate))) {
            candidate++;
        }
        return Math.min(candidate, end);
    }

    // Position right after the last occurrence of separator that ends within [from, to], or -1.
    private static int lastIndexAfter(String text, String separator, int from, int to) {
        int index = text.lastIndexOf(separator, to - separator.length());
        return index >= 0 && index + separator.length() >= from ? index + separator.length() : -1;
    }

    // Position right after ". ", "! ", "? " or "…" plus whitespace, ending within [from, to], or -1.
    private static int lastSentenceEnd(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char c = text.charAt(i - 1);
            if ((c == '.' || c == '!' || c == '?' || c == '…') && Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static int indexOfWhitespace(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int from, int to) {
        int i = from;
        while (i < to && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
