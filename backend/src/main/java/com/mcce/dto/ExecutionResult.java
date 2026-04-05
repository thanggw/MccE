package com.mcce.dto;

public class ExecutionResult {
	private String language;
	private String output;
	private String error;
	private Integer exitCode;
	private boolean success;

	public ExecutionResult() {}

	public ExecutionResult(
		String language,
		String output,
		String error,
		Integer exitCode,
		boolean success
	) {
		this.language = language;
		this.output = output;
		this.error = error;
		this.exitCode = exitCode;
		this.success = success;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public Integer getExitCode() {
		return exitCode;
	}

	public void setExitCode(Integer exitCode) {
		this.exitCode = exitCode;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}
}
