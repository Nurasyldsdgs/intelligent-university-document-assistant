package com.university.docassistant.service;

import com.university.docassistant.service.DocumentChunker.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void emptyAndBlankTextGiveNoChunks() {
        assertThat(chunker.split(null)).isEmpty();
        assertThat(chunker.split("")).isEmpty();
        assertThat(chunker.split(" \n\t ")).isEmpty();
    }

    @Test
    void shortTextIsOneTrimmedChunk() {
        List<Chunk> chunks = chunker.split("  Лабораторная работа 1.  \n");

        assertThat(chunks).containsExactly(new Chunk(0, 2, 24, "Лабораторная работа 1."));
    }

    @Test
    void prefersParagraphThenSentenceBoundaries() {
        String paragraph = "А".repeat(700) + ".\n\n";
        String sentences = "Первое предложение. ".repeat(60);
        List<Chunk> chunks = chunker.split(paragraph + sentences);

        assertThat(chunks.get(0).content()).endsWith("А.");
        assertThat(chunks.get(1).content()).endsWith("предложение.");
    }

    @Test
    void consecutiveChunksOverlapAtWordStarts() {
        String text = "слово ".repeat(1_000);
        List<Chunk> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(5);
        for (int i = 1; i < chunks.size(); i++) {
            Chunk previous = chunks.get(i - 1);
            Chunk current = chunks.get(i);
            int overlap = previous.endOffset() - current.startOffset();
            assertThat(overlap).isBetween(DocumentChunker.OVERLAP - 10, DocumentChunker.OVERLAP + 10);
            assertThat(current.content()).startsWith("слово");
        }
    }

    @Test
    void longWordWithoutSpacesIsCutAtMaxSize() {
        List<Chunk> chunks = chunker.split("x".repeat(2_500));

        assertThat(chunks).extracting(chunk -> chunk.content().length())
                .allMatch(length -> length <= DocumentChunker.MAX_CHUNK_SIZE);
        assertThat(chunks.get(chunks.size() - 1).endOffset()).isEqualTo(2_500);
    }

    @Test
    void neverSplitsSurrogatePairs() {
        List<Chunk> chunks = chunker.split("😀".repeat(1_500)); // 3 000 chars, no whitespace

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(Character.isLowSurrogate(chunk.content().charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(chunk.content().charAt(chunk.content().length() - 1))).isFalse();
        });
    }

    @Test
    void invariantsHoldOnRandomText() {
        Random random = new Random(42);
        String[] pieces = {"слово", "word", ".", "!", "?", " ", " ", " ", "\n", "\n\n", "очень-длинное-слово-без-пробелов"};
        for (int run = 0; run < 50; run++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(20_000);
            while (text.length() < length) {
                text.append(pieces[random.nextInt(pieces.length)]);
            }
            String source = text.toString();
            List<Chunk> chunks = chunker.split(source);

            int previousStart = -1;
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                assertThat(chunk.index()).isEqualTo(i);
                assertThat(chunk.content()).isEqualTo(source.substring(chunk.startOffset(), chunk.endOffset()));
                assertThat(chunk.content()).isNotBlank();
                assertThat(chunk.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_SIZE);
                assertThat(chunk.startOffset()).isGreaterThan(previousStart); // Always progresses.
                previousStart = chunk.startOffset();
            }
            if (!source.isBlank()) {
                // Nothing at the end is lost: the last chunk reaches the last non-whitespace character.
                assertThat(chunks.get(chunks.size() - 1).endOffset()).isEqualTo(source.stripTrailing().length());
            }
        }
    }

    @Test
    void previewIsShort() {
        Chunk chunk = chunker.split("слово ".repeat(100)).get(0);

        assertThat(chunk.preview()).hasSize(DocumentChunker.PREVIEW_LENGTH + 1).endsWith("…");
    }
}
