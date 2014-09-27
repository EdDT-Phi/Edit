package interpret;

import interpret.Edit.InterpreterException;

public class EditDemo {

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Usage: sbasic <filename>");
			return;
		}
		try {
			Edit ob = new Edit(args[0]);
			ob.run();
		} catch (InterpreterException exc) {
			System.out.println(exc);
		}
	}
}
