package com.university.docassistant.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares assignment titles the way a teacher would: "Лаб. работа №1", "Лабораторная работа 1" and "ЛАБ.1" are the
 * same assignment, "Лаб. 1" and "Лаб. 2" never are. Used to flag AI drafts that duplicate an existing assignment
 * (a syllabus and the assignment file both mention it); nothing is ever merged automatically.
 */
public final class AssignmentTitles {

    private AssignmentTitles() {
    }

    /** Kind of course work a title names, with its number: "лабораторная работа" 3. */
    public record WorkRef(String type, int number) {
    }

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern LETTER_THEN_DIGIT = Pattern.compile("(\\p{L})(\\d)");
    private static final Pattern DIGIT_THEN_LETTER = Pattern.compile("(\\d)(\\p{L})");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final String LAB = "лабораторная работа";
    private static final String PRACTICE = "практическая работа";
    private static final String SRSP = "срсп";
    private static final String SRS = "срс";

    /** Spellings of each kind of work, applied in order (СРСП before СРС); Latin look-alikes of с/о are accepted. */
    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\b(?:[сc]р[сc]п|[сc]р[оo]п|[сc] р [сc] п|[сc] р [оo] п"
                    + "|самостоятельн\\w* работ\\w* (?:студент\\w*|обучающ\\w*) (?:под руководством|с) преподавател\\w*)\\b",
                    FLAGS), SRSP),
            new Rule(Pattern.compile("\\b(?:[сc]р[сc]|[сc]р[оo]|[сc] р [сc]|[сc] р [оo]"
                    + "|самостоятельн\\w* работ\\w*(?: (?:студент\\w*|обучающ\\w*))?)\\b", FLAGS), SRS),
            new Rule(Pattern.compile("\\b(?:лабораторн\\w*|лаб(?:а|ы|у|ка|ки)?)(?: раб\\w*)?\\b", FLAGS), LAB),
            new Rule(Pattern.compile("\\b(?:практическ\\w*(?: (?:раб\\w*|занят\\w*))?|практик\\w*|пр(?: раб\\w*)?)\\b",
                    FLAGS), PRACTICE));

    private static final String TYPES = "(" + LAB + "|" + PRACTICE + "|" + SRSP + "|" + SRS + ")";
    private static final Pattern TYPE_THEN_NUMBER = Pattern.compile(TYPES + " (\\d+)\\b", FLAGS);
    private static final Pattern NUMBER_THEN_TYPE = Pattern.compile("\\b(\\d+) " + TYPES, FLAGS);

    /**
     * Lower case, "ё" as "е", without "№", punctuation and extra spaces, every spelling of a kind of work unified;
     * numbers are kept. "Лаб. работа №1: Сортировка" -> "лабораторная работа 1 сортировка". Never null.
     */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String text = title.toLowerCase(Locale.ROOT).replace('ё', 'е').replace("№", " ");
        text = LETTER_THEN_DIGIT.matcher(text).replaceAll("$1 $2");
        text = DIGIT_THEN_LETTER.matcher(text).replaceAll("$1 $2");
        text = NOT_ALPHANUMERIC.matcher(text).replaceAll(" ");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        for (Rule rule : RULES) {
            text = rule.pattern().matcher(text).replaceAll(rule.replacement());
        }
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    /** The first "kind of work + number" a normalized title names: "лабораторная работа 1" or "1 лабораторная работа". */
    public static Optional<WorkRef> workRef(String normalizedTitle) {
        Matcher typeFirst = TYPE_THEN_NUMBER.matcher(normalizedTitle);
        if (typeFirst.find()) {
            return number(typeFirst.group(1), typeFirst.group(2));
        }
        Matcher numberFirst = NUMBER_THEN_TYPE.matcher(normalizedTitle);
        if (numberFirst.find()) {
            return number(numberFirst.group(2), numberFirst.group(1));
        }
        return Optional.empty();
    }

    /**
     * Whether two assignments look like the same one: the same normalized title, or the same kind of work and number
     * with deadlines at most a day apart. The same kind with different numbers is never a duplicate.
     */
    public static boolean likelySame(String titleA, LocalDateTime deadlineA, String titleB, LocalDateTime deadlineB) {
        String a = normalize(titleA);
        String b = normalize(titleB);
        Optional<WorkRef> refA = workRef(a);
        Optional<WorkRef> refB = workRef(b);
        if (refA.isPresent() && refB.isPresent() && refA.get().type().equals(refB.get().type())
                && refA.get().number() != refB.get().number()) {
            return false;
        }
        if (!a.isEmpty() && a.equals(b)) {
            return true;
        }
        return refA.isPresent() && Objects.equals(refA, refB) && deadlineA != null && deadlineB != null
                && Duration.between(deadlineA, deadlineB).abs().compareTo(Duration.ofDays(1)) <= 0;
    }

    private static Optional<WorkRef> number(String type, String digits) {
        try {
            return Optional.of(new WorkRef(type, Integer.parseInt(digits)));
        } catch (NumberFormatException ex) {
            return Optional.empty(); // More digits than an int: not a work number.
        }
    }
}
