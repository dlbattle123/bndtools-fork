package aQute.lib.justif;

import java.util.*;
import java.util.Map.Entry;

/**
 * Formatter. This formatter allows you to build up an input string and then
 * wraps the text. The following markup is available
 * <ul>
 * <li>$- - Line over the remaining width
 * <li>\\t[0-9] - Go to tab position, and set indent to that position
 * <li>\\f - Newlin
 * </ul>
 */
public class Justif {
	final int[]		tabs;
	final int		width;
	StringBuilder	sb	= new StringBuilder();
	Formatter		f	= new Formatter(sb);

	public Justif(int width, int... tabs) {
		this.tabs = tabs == null || tabs.length == 0 ? new int[] {
				30, 40, 50, 60, 70
		} : tabs;
		this.width = width == 0 ? 73 : width;
	}

	public Justif() {
		this(0);
	}

	/**
	 * Routine to wrap a stringbuffer. Basically adds line endings but has the
	 * following control characters:
	 * <ul>
	 * <li>Space at the beginnng of a line is repeated when wrapped for indent.</li>
	 * <li>A tab will mark the current position and wrapping will return to that
	 * position</li>
	 * <li>A form feed in a tabbed colum will break but stay in the column</li>
	 * </ul>
	 * 
	 * @param sb
	 */
	public void wrap(StringBuilder sb) {
		List<Integer> indents = new ArrayList<Integer>();

		int indent = 0;
		int linelength = 0;
		int lastSpace = 0;
		int r = 0;
		boolean begin = true;

		while (r < sb.length()) {
			switch (sb.charAt(r++)) {
				case '\r' :
					indents.clear();
					sb.setCharAt(r - 1, '\n');
					// FALL THROUGH

				case '\n' :
					indent = indents.isEmpty() ? 0 : indents.remove(0);
					linelength = 0;
					begin = true;
					lastSpace = 0;
					break;

				case ' ' :
					if (begin)
						indent++;
					else {
						while (r < sb.length() && sb.charAt(r) == ' ')
							sb.delete(r, r + 1);
					}
					lastSpace = r - 1;
					linelength++;
					break;

				case '\t' :
					sb.deleteCharAt(--r);
					indents.add(indent);
					if (r < sb.length()) {
						char digit = sb.charAt(r);
						if (Character.isDigit(digit)) {
							sb.deleteCharAt(r);

							int column = (digit - '0');
							if (column < tabs.length)
								indent = tabs[column];
							else
								indent = column * 8;

							int diff = indent - linelength;
							if (diff > 0) {
								for (int i = 0; i < diff; i++) {
									sb.insert(r, ' ');
								}
								r += diff;
								linelength += diff;
							}
						} else
							System.err.println("missing digit after \t");
					}
					break;

				case '\f' :
					sb.setCharAt(r - 1, '\n');
					for (int i = 0; i < indent; i++) {
						sb.insert(r, ' ');
					}
					r += indent;
					while (r < sb.length() && sb.charAt(r) == ' ')
						sb.delete(r, r + 1);
					linelength = 0;
					lastSpace = 0;
					break;

				case '$' :
					if (sb.length() > r) {
						char c = sb.charAt(r);
						if (c == '-' || c == '_' || c == '\u2014') {
							sb.delete(r - 1, r); // remove $
							begin = false;
							linelength++;
							while (linelength < width - 1) {
								sb.insert(r++, c);
								linelength++;
							}
							break;
						}
					}

				case '\u00A0' : // non breaking space
					sb.setCharAt(r - 1, ' '); // Turn it into a space

					// fall through

				default :
					linelength++;
					begin = false;
					if (linelength > width) {
						if (lastSpace == 0) {
							lastSpace = r-1;
							sb.insert(lastSpace, ' ');
							r++;
						}
						sb.setCharAt(lastSpace, '\n');
						linelength = r-lastSpace -1;

						for (int i = 0; i < indent; i++) {
							sb.insert(lastSpace + 1, ' ');
							linelength++;
						}
						r += indent;
						lastSpace = 0;
					}
			}
		}
	}

	public String wrap() {
		wrap(sb);
		return sb.toString();
	}

	public Formatter formatter() {
		return f;
	}

	public String toString() {
		wrap(sb);
		return sb.toString();
	}

	public void indent(int indent, String string) {
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (i == 0) {
				for (int j = 0; j < indent; j++)
					sb.append(' ');
			} else {
				sb.append(c);
				if (c == '\n')
					for (int j = 0; j < indent; j++)
						sb.append(' ');
			}
		}
	}

	// TODO not working yet

	public void entry(String key, String separator, Object value) {
		sb.append(key);
		sb.append("\t1");
		sb.append(separator);
		sb.append("\t2");
	}

	public void table(Map<String,Object> table, String separator) {
		for (Entry<String,Object> e : table.entrySet()) {
			entry(e.getKey(), separator, e.getValue());
			formatter().format("\r");
		}
	}

	public String toString(Object o) {
		String s = "" + o;
		if (s.length() > 50)
			return s.replaceAll(",", ", \\\f");
		return s;
	}
}
