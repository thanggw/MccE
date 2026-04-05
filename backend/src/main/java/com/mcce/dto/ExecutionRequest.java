package com.mcce.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public class ExecutionRequest {
	private String language;
	private String entryFile;
	private String stdin;
	private List<File> files;

	public ExecutionRequest() {}

	public ExecutionRequest(String language, String entryFile, String stdin, List<File> files) {
		this.language = language;
		this.entryFile = entryFile;
		this.stdin = stdin;
		this.files = files;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getEntryFile() {
		return entryFile;
	}

	public void setEntryFile(String entryFile) {
		this.entryFile = entryFile;
	}

	public String getStdin() {
		return stdin;
	}

	public void setStdin(String stdin) {
		this.stdin = stdin;
	}

	public List<File> getFiles() {
		return files;
	}

	public void setFiles(List<File> files) {
		this.files = files;
	}

	public static class File {
		private String name;
		private String path;
		private String content;

		public File() {}

		public File(String name, String path, String content) {
			this.name = name;
			this.path = path;
			this.content = content;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		@JsonIgnore
		public String getResolvedPath() {
			if (path != null && !path.trim().isEmpty()) {
				return path;
			}
			return name;
		}
	}
}
