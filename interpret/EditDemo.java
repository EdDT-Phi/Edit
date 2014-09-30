package interpret;

import interpret.Edit.InterpreterException;

public class EditDemo {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: sbasic <filename> <options>");
			return;
		}
		try {
			Edit ob = new Edit(args[0]);
			ob.run(toBoolean(args[1]));
		} catch (InterpreterException exc) {
			System.out.println(exc);
		}
	}
	
	public static boolean toBoolean(String s){
		return s.equals("true");
	}
}
