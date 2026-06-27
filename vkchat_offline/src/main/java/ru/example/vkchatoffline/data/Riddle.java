package ru.example.vkchatoffline.data;

import java.util.List;

public class Riddle {
    private final String question;
    private final List<String> answers;
    private final String successReward;
    private final String failReward;

    public Riddle(String question, List<String> answers, String successReward, String failReward) {
        this.question = question;
        this.answers = answers;
        this.successReward = successReward;
        this.failReward = failReward;
    }

    public String getQuestion() { return question; }
    public List<String> getAnswers() { return answers; }
    public String getSuccessReward() { return successReward; }
    public String getFailReward() { return failReward; }
}
