package com.mcce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Judge0SubmissionRequest {

	@JsonProperty("language_id")
	private Integer languageId;

	@JsonProperty("source_code")
	private String sourceCode;

	private String stdin;

	public Judge0SubmissionRequest() {}

	public Judge0SubmissionRequest(Integer languageId, String sourceCode, String stdin) {
		this.languageId = languageId;
		this.sourceCode = sourceCode;
		this.stdin = stdin;
	}

	public Integer getLanguageId() {
		return languageId;
	}

	public void setLanguageId(Integer languageId) {
		this.languageId = languageId;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getStdin() {
		return stdin;
	}

	public void setStdin(String stdin) {
		this.stdin = stdin;
	}
}
