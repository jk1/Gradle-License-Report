package com.smokejumperit.gradle.report;

import org.apache.commons.lang3.StringEscapeUtils;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.SimpleNodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.AbstractStyledTextOutput;
import org.gradle.util.GUtil;

import java.util.*;
import java.io.*;

import com.google.common.collect.ImmutableSet;
import static com.google.common.base.Preconditions.*;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

import org.gradle.api.artifacts.*;

/**
* Prints out an HTML report of third party license information.
*/
public abstract class ThirdPartyLicenseReport implements DependencyReportRenderer {

	private static enum State {
		INIT, OPEN, CLOSED
	}

	public class HtmlStyledTextOutput extends AbstractStyledTextOutput implements AutoCloseable {

		private final File file;
		private volatile int headerCounter = 1; // Yeah, I should use an AtomicInteger here. Bah.
		private volatile Writer writer;
		private final AtomicReference<State> currentState = new AtomicReference(State.INIT);
		private StyledTextOutput.Style style = null;
		private String closeStyle = "";

		public HtmlStyledTextOutput(File file) {
			checkNotNull(file, "file");
			this.file = file;
		}

    public StyledTextOutput println() {
			html("<br />");
			return this;
    }

		public synchronized StyledTextOutput html(String toWrite) {
			if(text != null && !text.equals("")) {
				if(currentState.compareAndSet(State.INIT, State.OPEN)) {
					openWriter();
					checkNotNull(writer, "writer was not opened by openWriter()");
					writePrelude();
				}
				checkNotNull(writer, "writer");
				write(toWrite);
			}
			return this;
		}

    protected void doAppend(String text) {
			html(escapeHtml(text));
		}

    protected synchronized void doStyleChange(final StyledTextOutput.Style style) {
			if(style == null) {
				doStyleChange(StyledTextOutput.Style.Normal);
			} else if(style.equals(this.style)) {
				return;
			}

			this.style = style;
			html(closeStyle);

			switch(style) {
				case StyledTextOutput.Style.Normal:
					html("<p>");
					closeStyle = "</p>";
				break;
				case StyledTextOutput.Style.Header:
					// Potential race condition here, but who cares?
					int headCtr = headerCounter;
					html("<h" + headCtr + ">");
					closeStyle = "</h" + headCtr + ">";
					headerCounter = headCtr + 1;
				break;
				default:
					html("<span class=\"" + style.toString() + "\">");
					closeStyle = "</span>";
				break;
			}
    }

		public void resetHeaderCounter(int value) {
			headerCounter = Math.max(1,Math.min(6,value));
		}

		public void startTable() {
			doStyleChange(null);
			html("<table>");
		}

		public void endTable() {
			doStyleChange(null);
			html("</table>");
		}

		public void startTableRow() {
			doStyleChange(null);
			html("<tr>");
		}

		public void endTableRow() {
			doStyleChange(null);
			html("</tr>");
		}

		public void startTableCell() {
			doStyleChange(null);
			html("<td>");
		}

		public void endTableCell() {
			doStyleChange(null);
			html("</td>");
		}

		public void cell(String content) {
			startTableCell();
			text(content);
			endTableCell();
		}

		public void link(String message, String url) {
			html("<a href=\"" + url + "\">" + escapeHtml(message) + "</a>");
		}

		public void linkToContent(String message, String contentName, String content) {
			checkNotNull(message, "message");
			checkNotNull(contentName, "contentName");
			checkNotNull(content, "content");

			final File contentFile;
			if(file.isDirectory()) {
				contentFile = new File(file, contentName);
			} else {
				contentFile = new File(file.getParentFile(), contentName);
			}
			contentFile.getParentFile().mkdirs();
			FileUtils.write(contentFile, content, false);

			link(message, contentName);
		}

		protected String escapeHtml(String text) {
			if(text == null || "".equals(text)) return text;
			return StringEscapeUtils.escapeHtml4(text);
		}

		protected synchronized void openWriter() {
			final File toWriteTo;
			if(file.exists() && !file.isDirectory()) {
				toWriteTo = file;
			} else if(!file.exists()) {
				if(!file.getParentFile().exists()) {
					file.getParentFile.mkdirs();
				}
				file.createNewFile();
				toWriteTo = file;
			} else if(file.isDirectory()) {
				if(!file.exists()) file.mkdirs();
				toWriteTo = new File(file, "index.html");
			} else {
				throw new RuntimeException("No idea what to do with this file: " + file.getAbsolutePath());
			}
			writer = new BufferedWriter(new FileWriter(toWriteTo, false));
		}

		protected void writePrelude() {
			checkNotNull(writer, "writer");
			write("<html><head><title>" + escapeHtml(project.getName()) + " Third Party Dependency License Report</title></head><body>");
		}

		protected void writePostlude() {
			write(closeStyle);
			closeStyle = "";
			write("</body></html>");
		}

		public void close() {
			if(currentState.compareAndSet(State.OPEN, State.CLOSED)) {
				writePostlude();
				IOUtils.closeQuietly(writer);
				style = null;
				closeStyle = null;
				writer = null;
			}
		}

		private final void write(String str) {
			checkNotNull(writer, "writer is null (closed?)");
			try {
				writer.write(str);
			} catch(IOException ioe) {
				throw new RuntimeException("Error writing to " + file, ioe);
			}
		}

		protected void finalize() throws Throwable	{
			close();
		}

	}

	private final Project project;
	private final PomHunter pom = new PomHunter(project);
	private final FileHunter files = new FileHunter(project);
	private volatile HtmlStyledTextOutput output;

	public ThirdPartyLicenseReport(Project project) {
		checkNotNull(project, "project");
		this.project = project;
	}


	@Override
	public HtmlStyledTextOutput getTextOutput() {
		return output;
	}

	public HtmlStyledTextOutput getOutput() {
		return output;
	}

	public void setOutput(HtmlStyledTextOutput textOutput) {
		checkNotNull(textOutput, "textOutput");
		this.output = textOutput;
	}

	/**
	 * Sets the text output for the report. This method must be called before any other methods on this renderer,
	 * and must pass in an {@link HtmlStyledTextOutput}.
	 *
	 * @param textOutput The text output, never null.
	 * @throws ClassCastException If the parameter is not a {@link HtmlStyledTextOutput}.
	 */
	@Override
	public void setOutput(StyledTextOutput textOutput) {
		setOutput((HtmlStyledTextOutput)textOutput);
	}

	/**
	 * Sets the output file for the report. This method must be called before any other methods on this renderer.
	 *
	 * @param file The output file, never null.
	 */
	@Override
	public void setOutputFile(File file) throws IOException {
		setOutput(new HtmlStyledTextOutput(file));
	}

	/**
	 * Starts visiting a project.
	 *
	 * @param project The project, never null.
	 */
	@Override
	public void startProject(Project project) {
		output.resetHeaderCounter(1);
		output.withStyle(StyledTextOutput.Style.Header).text(project.getName());
		if(project.getDescription() != null) output.text(project.getDescription());
	}

	public void render(Project project) {
		Configurations configs = project.getConfigurations().matching(new Spec<Configuration>() {
			public boolean isSatisfiedBy(Configuration config) {
				return !skipConfigNames.contains(config.getName());
			}
		});

		for(Configuration config : configs) {
			startConfiguration(config);
			render(config);
			completeConfiguration(config);
		}
	}

	/**
	 * Completes visiting a project.
	 *
	 * @param project The project, never null.
	 */
	@Override
	public void completeProject(Project project) {
		output.println();
	}

	/**
	 * Completes this report. This method must be called last on this renderer.
	 */
	@Override
	public void complete() throws IOException {
		output.close();
	}


	/**
	* Starts rendering the given configuration.
	* @param configuration The configuration.
	*/
	@Override
	public void startConfiguration(Configuration configuration) {
		output.resetHeaderCounter(2);
		output.withStyle(StyledTextOutput.Style.Header).text(configuration.getName());
		if(configuration.getDescription() != null) output.text(configuration.getDescription());
		output.startTable();

		writeHeaderRow();
	}

	protected void writeHeaderRow() {
		output.startTableRow();
		output.html("<th>");
		output.withStyle(StyledTextOutput.Style.Header).text("Library Name");
		output.html("</th>");
		output.html("<th>Version</th>");
		output.html("<th>Organization<br />(Package)</th>");
		output.html("<th>Library URL</th>");
		output.html("<th>Published<br />License</th>");
		output.html("<th>Files</th>");
		output.endTableRow();
	}

	/**
	* Writes the given dependency graph for the current configuration.
	*
	* @param configuration The configuration.
	*/
	@Override
	public void render(Configuration configuration) throws IOException {
		for(ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			startArtifact(artifact);
			render(artifact);
			completeArtifact(artifact);
		}
	}

	/**
	* Completes the rendering of the given configuration.
	* @param configuration The configuration
	*/
	@Override
	public void completeConfiguration(Configuration configuration) {
		output.endTable();
		output.println();
	}

	public void startArtifact(ResolvedArtifact artifact) {
		output.startTableRow();
		output.resetHeaderCounter(3);
	}

	public void endArtifact(ResolvedArtifact artifact) {
		output.endTableRow();
	}

	public void render(ResolvedArtifact artifact) {
		output.style(StyledTextOutput.Style.Normal);
		writeName(artifact);
		writeVersion(artifact);
		writePackage(artifact);
		writeUrl(artifact);
		writeLicense(artifact);
		writeFiles(artifact);
	}

	protected void writeName(ResolvedArtifact artifact) {
		output.cell(artifact.getName());
	}

	protected void writeVersion(ResolvedArtifact artifact) {
		output.cell(artifact.getModuleVersion().getId().getVersion());
	}

	protected void writePackage(ResolvedArtifact artifact) {
		output.cell(artifact.getModuleVersion().getId().getGroup());
	}

	protected void writeUrl(ResolvedArtifact artifact) {
		PomData data = pom.forModuleVersion(artifact.getModuleVersion().getId());
		if(data == null) {
			output.cell("");
			return;
		}
		String url = data.getUrl();
		if(url == null || "".equals(url)) {
			output.cell("");
			return;
		}
		output.startTableCell();
		output.link(url, url);
		output.endTableCell();
	}

	protected void writeLicense(ResolvedArtifact artifact) {
		PomData data = pom.forModuleVersion(artifact.getModuleVersion().getId());
		if(data == null) {
			output.cell("");
			return;
		}
		String license = data.getLicense();
		if(license == null || "".equals(license)) {
			output.cell("");
			return;
		}
		String licenseUrl = data.getLicenseUrl();
		if(licenseUrl == null || "".equals(licenseUrl)) {
			output.cell(license);
			return;
		}

		output.startTableCell();
		output.link(license, licenseUrl);
		output.endTableCell();
	}

	protected void writeFiles(ResolvedArtifact artifact) {
		boolean firstEntry = true;
		output.startTableCell();
		for(Map.Entry<String,String> fileWithContent : files.forArtifact(artifact).entries()) {
			if(firstEntry) {
				firstEntry = false;
			} else {
				output.println();
			}

			output.linkToContent(fileWithContent.getKey(), artifact.getName() + "/" + fileWithContent.getKey(), fileWithContent.getValue());
		}
		output.endTableCell();
	}

}

