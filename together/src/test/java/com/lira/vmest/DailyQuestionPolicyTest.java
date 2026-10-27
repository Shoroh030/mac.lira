package com.lira.vmest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DailyQuestionPolicyTest {
    @Test
    public void selectsTheSameQuestionForTheSameDay() {
        long day = 20_000L;

        assertEquals(
                DailyQuestionPolicy.questionForDay(day, Collections.emptyList()),
                DailyQuestionPolicy.questionForDay(day, Collections.emptyList()));
    }

    @Test
    public void rotatesBuiltInQuestionsAcrossDays() {
        long day = 20_000L;

        assertNotEquals(
                DailyQuestionPolicy.questionForDay(day, Collections.emptyList()),
                DailyQuestionPolicy.questionForDay(day + 1, Collections.emptyList()));
    }

    @Test
    public void customQuestionAddedTodayBecomesTheSharedQuestion() {
        long day = 20_000L;
        List<DailyQuestionPolicy.Prompt> custom = Arrays.asList(
                new DailyQuestionPolicy.Prompt("custom-id", "Наш вопрос на сегодня?", day - 1),
                new DailyQuestionPolicy.Prompt("today-id", "Куда поедем вместе?", day));

        assertEquals("Куда поедем вместе?", DailyQuestionPolicy.questionForDay(day, custom));
    }

    @Test
    public void customQuestionOrderingDoesNotDependOnDeviceInsertionOrder() {
        long day = 20_000L;
        List<DailyQuestionPolicy.Prompt> firstOrder = Arrays.asList(
                new DailyQuestionPolicy.Prompt("b", "Вопрос Б?", day),
                new DailyQuestionPolicy.Prompt("a", "Вопрос А?", day));
        List<DailyQuestionPolicy.Prompt> secondOrder = Arrays.asList(
                new DailyQuestionPolicy.Prompt("a", "Вопрос А?", day),
                new DailyQuestionPolicy.Prompt("b", "Вопрос Б?", day));

        assertEquals(
                DailyQuestionPolicy.questionForDay(day, firstOrder),
                DailyQuestionPolicy.questionForDay(day, secondOrder));
        assertTrue(DailyQuestionPolicy.builtInQuestionCount() >= 20);
    }
}
