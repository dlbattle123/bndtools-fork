package aQute.bnd.readme;

import java.util.Optional;

final public class ReadmeWorkspaceOption extends ReadmeOption {

	private ReadmeInformation _information;

	private ReadmeWorkspaceOption() {

	}

	public static Optional<ReadmeWorkspaceOption> parse(final String properties, final String rootPath) {
		ReadmeWorkspaceOption option = new ReadmeWorkspaceOption();

		if (option.parse(properties, rootPath, false)) {

			option.extractInformation(null);

			return Optional.of(option);

		} else {

			return Optional.empty();
		}
	}

	@Override
	public ReadmeInformation getReadmeInformation() {

		return _information;
	}

	/**
	 * TODO How to do this, need to extract info from build.bnd global header if
	 * possible need to know the source type, and where to use this class in bnd
	 * (project option are parsed from the Build#doReadme) For now, workspace
	 * readme are not handled.
	 */
	private void extractInformation(Object source) {

		_information = ReadmeInformation.builder().build();
	}
}
