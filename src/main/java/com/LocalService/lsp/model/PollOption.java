package com.LocalService.lsp.model;

public class PollOption {
    private String optionId;
    private String text;
    private int voteCount = 0;

    public PollOption() {}

    public PollOption(String optionId, String text, int voteCount) {
        this.optionId = optionId;
        this.text = text;
        this.voteCount = voteCount;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }
}
