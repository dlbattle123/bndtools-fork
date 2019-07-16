package biz.aQute.bnd.reporter.gogo.dto;

import java.util.List;

import org.osgi.dto.DTO;

public class GogoParameterDTO extends DTO {

	/**
	 * The order of the parameter.
	 */
	public int		order;

	/**
	 * Title of this parameter.
	 */
	public String	title;

	/**
	 * Description of this parameter.
	 */
	public String	description;

	/**
	 * The default value of the parameter if its name is present on the command
	 * line.
	 */
	public String	presentValue;

	/**
	 * The default value of the parameter if its name is not present on the
	 * command line.
	 */
	public String	absentValue;

	/**
	 * List of names of this parameter.
	 */
	List<String>	names;
}
