package com.mcce.dto;

public class CollaborationEditorChangeDto {
	private int rangeOffset;
	private int rangeLength;
	private String text;

	public CollaborationEditorChangeDto() {}

	public CollaborationEditorChangeDto(int rangeOffset, int rangeLength, String text) {
		this.rangeOffset = rangeOffset;
		this.rangeLength = rangeLength;
		this.text = text;
	}

	public int getRangeOffset() {
		return rangeOffset;
	}

	public void setRangeOffset(int rangeOffset) {
		this.rangeOffset = rangeOffset;
	}

	public int getRangeLength() {
		return rangeLength;
	}

	public void setRangeLength(int rangeLength) {
		this.rangeLength = rangeLength;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
