package com.lira.vmest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class DailyQuestionPolicy {
    private static final String[] BUILT_IN_QUESTIONS = {
            "Какое наше общее воспоминание тебе хочется пережить ещё раз?",
            "Какой маленький знак заботы от меня особенно радует тебя?",
            "Куда бы ты хотел(а) отправиться со мной на выходные?",
            "Какой наш обычный день кажется тебе по-настоящему счастливым?",
            "Что нового тебе хотелось бы попробовать вместе?",
            "За что ты сегодня особенно благодарен(на) нашим отношениям?",
            "Какой фильм или сериал нам стоит посмотреть вместе?",
            "Какой совместный план давно пора превратить в реальность?",
            "Что помогает тебе почувствовать, что я рядом?",
            "Какой смешной момент между нами ты часто вспоминаешь?",
            "Как бы выглядело наше идеальное свободное воскресенье?",
            "Какую песню ты бы выбрал(а) саундтреком к нашей истории?",
            "Чему ты хотел(а) бы научиться вместе со мной?",
            "Какое блюдо нам стоит приготовить вдвоём?",
            "В какой момент ты понял(а), что нам хорошо вместе?",
            "Какой подарок-впечатление мы могли бы сделать друг другу?",
            "Что в моём характере тебе нравится больше всего?",
            "Какое место рядом с нами заслуживает совместной поездки?",
            "Какой семейной или личной традиции нам не хватает?",
            "Что я могу сделать, чтобы твой завтрашний день стал легче?",
            "Какое наше фото лучше всего передаёт настроение «мы»?",
            "Что из нового ты хотел(а) бы добавить в нашу жизнь?",
            "Какой комплимент тебе особенно приятно слышать от меня?",
            "Если бы у нас был целый день без дел, как бы мы его провели?",
            "Какой общий маленький ритуал нам стоит придумать?",
            "Что из того, что мы уже преодолели, делает тебя гордым(ой) за нас?",
            "Какой вопрос ты давно хотел(а) мне задать?",
            "Что для тебя значит чувствовать себя любимым(ой)?",
            "Какую мечту мы могли бы начать осуществлять уже на этой неделе?",
            "Какой момент сегодняшнего дня ты хотел(а) бы разделить со мной?"
    };

    static final class Prompt {
        final String id;
        final String text;
        final long addedOnDay;

        Prompt(String id, String text, long addedOnDay) {
            this.id = id == null ? "" : id;
            this.text = text == null ? "" : text.trim();
            this.addedOnDay = addedOnDay;
        }
    }

    private DailyQuestionPolicy() {}

    static String questionForDay(long epochDay, List<Prompt> customQuestions) {
        ArrayList<Prompt> valid = new ArrayList<>();
        if (customQuestions != null) {
            for (Prompt prompt : customQuestions) {
                if (prompt != null && !prompt.text.isEmpty()) valid.add(prompt);
            }
        }
        Collections.sort(valid, Comparator.comparing(prompt -> prompt.id));

        for (Prompt prompt : valid) {
            if (prompt.addedOnDay == epochDay) return prompt.text;
        }

        int total = BUILT_IN_QUESTIONS.length + valid.size();
        int selected = Math.floorMod(epochDay, total);
        if (selected < BUILT_IN_QUESTIONS.length) return BUILT_IN_QUESTIONS[selected];
        return valid.get(selected - BUILT_IN_QUESTIONS.length).text;
    }

    static int builtInQuestionCount() {
        return BUILT_IN_QUESTIONS.length;
    }
}
