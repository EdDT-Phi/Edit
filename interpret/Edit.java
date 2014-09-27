package interpret;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Stack;
import java.util.TreeMap;

// Variables aren't local

class Edit {

	final int MAX_PROG_SIZE = 100000;

	final boolean DEBUG = false;
	final boolean DEBUG_E = false;

	// Item types
	final int NONE = 0; // Don't know
	final int DELIMITER = 1; // Any weird symbols (not the rest) incl bool ops
	final int VARIABLE = 2; // If has been initiated
	final int COMMAND = 3; // If is in next list
	final int EOL = 4; // End of the line
	final int EOP = 5; // End of the program

	// Evaluable item types
	final int STRING = 6; // If quoted
	final int NUMBER = 7; // Well...
	final int BOOLEAN = 8; // Keeps as string
	final int VOID = 9; // Only for function

	// Internal representation of Edit commands
	final int UNKNCOM = 0; // Don't Know
	final int PRINT = 1; // Duh
	final int INPUT = 2; // Ask for...
	final int RETURN = 3; // End function prematurely or return val
	final int THEN = 4; // Expected after If
	final int END = 5; // Ends some commands
	final int DO = 6; // Expected after while

	// End is expected after these
	final int IF = 7; // Start If
	final int FOR = 8; // Start For
	final int WHILE = 9; // Start While
	final int FUNCTION = 10; // Creates new Functions

	// Errors
	final int SYNTAX = 0; // Unexpected stuff
	final int UNBALPARENS = 1; // (... or ...)
	final int DIVBYZERO = 2; // 1/0
	final int EQUALEXPECTED = 3; // For variable assignment and if statements
	final int NOTAVAR = 4; // for vars that have no value: assignments,loops
	final int NOTABOOL = 5; // not a boolean
	final int NOTANUMB = 6; // not a number
	final int NOTASTR = 7; // not a string
	final int DUPFUNCTION = 8; // two functions with same name
	final int ENDEXPECTED = 9; // Reaches end of program without end
	final int THENEXPECTED = 10; // no then after if
	final int MISSQUOTE = 11; // strings missing a quote
	final int UNKFUNCTION = 12; // Unknown function
	final int INVALIDEXP = 13; // Invalid Expression
	final int UNEXPITEM = 14;

	final int FILENOTFOUND = 16; // can't find file
	final int INPUTIOERROR = 17; // Input that fails
	final int EXPERR = 18; // for if, while and for
	final int FILEIOERROR = 15; // can't load file

	final int UNKNOWN = 19;

	// Codes for operators such as <=
	final char LE = 0; // <=
	final char GE = 1; // >=
	final char EQ = 4; // ==

	final char rOps[] = { LE, GE, '<', '>', EQ };
	String relops = new String(rOps);

	// Codes for boolean operators
	final char AND = 0;
	final char OR = 1;
	final char NOT = 2;
	final char XOR = 3;
	final char XAND = 4;

	final char bOpsId[] = { AND, OR, NOT, XOR, XAND };
	final String bOps[] = { "and", "or", "not", "xor", "xand" };

	String[] commTable = { "", "print", "input", "then", "return", "end", "do",
			"if", "for", "while", "function" };

	class ForInfo {
		String var;
		double endVal;
		int loopLoc;
	}

	class Function {
		String name;
		int functLoc;

		public Function(String n, int c) {
			name = n;
			functLoc = c;
		}
	}

	private Stack<ForInfo> forStack; // nested loops
	private Stack<Function> functStack; // recursive functions? TODO

	private TreeMap<String, Object> vars; // holds all vars
	private ArrayList<Function> functs; // holds all functions

	private char[] prog; // holds all the program
	private int progIdx; // current program index
	private int progLine; // current program line

	private String item; // the current word/char/number
	private int itemType; // from the types
	private int commType; // from the comms

	// Constructor for Edit
	public Edit(String progname) throws InterpreterException {

		char tempprog[] = new char[MAX_PROG_SIZE];
		int size;

		// Returns correct size
		size = loadProgram(tempprog, progname);

		if (size != -1) {
			prog = new char[size];

			System.arraycopy(tempprog, 0, prog, 0, size);
		}
	}

	// Load a program
	public int loadProgram(char[] p, String progname)
			throws InterpreterException {

		int size = 0;

		try {
			FileReader fr = new FileReader(progname);

			BufferedReader br = new BufferedReader(fr);

			size = br.read(p, 0, MAX_PROG_SIZE);

			fr.close();
		} catch (FileNotFoundException exc) {
			handleErr(FILENOTFOUND);
		} catch (IOException exc) {
			handleErr(FILEIOERROR);
		}

		// If file ends with an EOF mark, back up
		if (p[size - 1] == (char) 26)
			size--;

		return size;
	}

	// Execute the program
	public void run() throws InterpreterException {

		// Initialize to run a new program
		vars = new TreeMap<String, Object>();
		functs = new ArrayList<Function>();

		forStack = new Stack<ForInfo>();
		functStack = new Stack<Function>(); // TODO

		progIdx = 0;
		progLine = 1;

		// Let's get this started(Runs code)
		runCode();
	}

	// Let's go! (Runs code)
	private void runCode() throws InterpreterException {

		// Runs until
		while (nextItem()) {

			// Not exisiting funct or var, so new var
			if (itemType == VARIABLE) {
				assignVar();
			} else { // Start of line, must be command or function
				if (DEBUG)
					System.out.println("\n\tCommand:" + commType);

				switch (commType) {
				case PRINT:
					print();
					break;
				case INPUT:
					input();
					break;
				case IF:
					execIf();
					break;
				case FOR:
					execFor();
					break;
				case END:
					endComm();
					break;
				case WHILE:
					execWhile();
					break;
				case RETURN:
					execReturn();
					break;
				case FUNCTION:
					execFunction();
					break;
				}

				if (DEBUG)
					System.out.println("\n\tDone with command");
			}

			// All functions end on their line
			if (itemType != EOL && itemType != EOP) {
				handleErr(UNEXPITEM);
			}
			progLine++;
		}
	}

	private void assignVar() throws InterpreterException {
		String var;

		// get the variable name
		var = item;

		if (!Character.isLetter(var.charAt(0))) {
			handleErr(NOTAVAR);
			return;
		}

		// get equal sign
		nextItem();
		if (!item.equals("=")) {
			handleErr(EQUALEXPECTED);
			return;
		}

		nextItem();

		// check if next is number or string

		vars.put(var, evaluate());
	}

	// Value Type must be handled when calling!!
	private Object getVarVal(String vname) throws InterpreterException {
		if (!Character.isLetter(vname.charAt(0))) {
			handleErr(NOTAVAR);
			return 0;
		}
		return vars.get(vname); // return Object
	}

	// simple print command
	private void print() throws InterpreterException {

		String lastDelim = "";

		while (nextItem() && itemType != EOL && itemType != EOP) {

			System.out.print(evaluate());

			lastDelim = item;

			if (lastDelim.equals(",")) // ',' means one more item
				System.out.print(" ");
			else if (lastDelim.equals(";"))
				System.out.print("\t");
			else if (itemType != EOL && itemType != EOP)
				// also not end of line?
				handleErr(SYNTAX);
			else
				break;
		}

		System.out.println();
	}

	private void execFunction() throws InterpreterException {
		// TODO
	}

	private void execReturn() {
		// TODO Auto-generated method stub
	}

	private void execWhile() {
		// TODO Auto-generated method stub
	}

	private void endComm() {
		// TODO
	}

	private void execIf() throws InterpreterException {
		boolean result;

		try {
			result = (boolean) evaluate();
		} catch (ClassCastException exc) {
			handleErr(NOTABOOL);
			return;
		}

		if (result) { // Execute the If
			nextItem(); // Throw away Then
			if (itemType != THEN) {
				handleErr(THENEXPECTED);
				return;
			}
		} else {
			nextEnd(); // Skip this If
		}
	}

	// for loops
	private void execFor() throws InterpreterException {
		ForInfo newFor = new ForInfo();
		double value;
		String vname;

		nextItem(); // control variable
		vname = item;

		if (!Character.isLetter(vname.charAt(0))) {
			handleErr(NOTAVAR);
			return;
		}

		nextItem(); // =
		if (!item.equals("=")) {
			handleErr(EQUALEXPECTED);
			return;
		}

		try {
			value = (double) evaluate(); // initial value
		} catch (ClassCastException exc) {
			handleErr(EXPERR);
			return;
		}

		vars.put(vname, value); // add value to map

		try {
			newFor.endVal = (double) evaluate(); // end value
		} catch (ClassCastException exc) {
			handleErr(EXPERR);
			return;
		}

		// can run once
		if (value >= newFor.endVal) {
			newFor.loopLoc = progIdx;
			forStack.push(newFor); // add to stack
		} else {
			nextEnd(); // else skip it all
		}
	}

	// Input stringsn that start with number = bad
	private void input() throws InterpreterException {
		String var;
		double val = 0;
		String str = "";

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		// See if next has question to output
		nextItem();
		if (itemType == STRING) {
			System.out.print(item);
			nextItem(); // discard the comma
			if (!item.equals(",")) {
				handleErr(SYNTAX);
				return;
			}
			nextItem(); // get input var
		} else {
			// already has input var
			System.out.print("? "); // default question
		}

		// check for string, makes var
		if (!Character.isLetter(item.charAt(0))) {
			handleErr(NOTAVAR);
			return;
		}

		var = item;

		try {
			str = br.readLine();

			if (isNumber(str.charAt(0))) { // If number, convert
				try {
					val = Double.parseDouble(str);
					vars.put(var, val);
				} catch (NumberFormatException exc) {// If error save as string
					vars.put(var, str);
				}
			} else { // Not number, save as string
				vars.put(var, str);
			}
		} catch (IOException e) { // Idk what happend
			handleErr(INPUTIOERROR);
			return;
		}
	}

	// Get next End, used to skip functions
	private void nextEnd() throws InterpreterException {

		int count = 0;
		while (nextItem() && itemType != END && count > 0) {
			if (itemType > 6) // Loops and functions and stuff
				count++;
		}

		if (itemType != END) {
			handleErr(ENDEXPECTED);
			return;
		}
	}

	// Obtain next item, returns false if EOP
	private boolean nextItem() throws InterpreterException {
		boolean result = getNext();
		if (DEBUG) {
			System.out.println("\nNew item");
			System.out.println("Item: " + item);
			System.out.println("ItemType: " + itemType);
		}
		return result;
	}

	//
	//
	//
	// *********************************************NEXTITEM*******************************************
	//
	//
	//

	private boolean getNext() throws InterpreterException {

		char ch = ' ';

		item = "";
		itemType = NONE;
		commType = UNKNCOM;

		// Skip over white space.
		while (progIdx < prog.length && isSpaceOrTab(prog[progIdx]))
			progIdx++;

		// Check for end of program.
		if (progIdx >= prog.length) {
			itemType = EOP;
			item = "\r\n";
			return false;
		}

		// Check for end of line
		if (prog[progIdx] == '\r') {
			progIdx += 2;
			itemType = EOL;
			item = "\r\n";
			return true;
		}

		// Check for char values
		ch = prog[progIdx];

		// Reletional operators
		if (ch == '<' || ch == '>' || ch == '=') {
			switch (ch) {
			case '<':
				if (prog[progIdx + 1] == '=') {
					item = String.valueOf(LE);
					progIdx += 2;
				} else {
					item = "<";
					progIdx++;
				}
				break;
			case '>':
				if (prog[progIdx + 1] == '=') {
					item = String.valueOf(GE);
					progIdx += 2;
				} else {
					item = ">";
					progIdx++;
				}
				break;
			case '=':
				if (prog[progIdx + 1] == '=') {
					item = String.valueOf(EQ);
					progIdx += 2;
				} else {
					item = "=";
					progIdx++;
				}
				break;

			}
			itemType = DELIMITER;
			return true;
		}

		if (isDelim(ch)) { // Is an operator.
			item += prog[progIdx];
			progIdx++;
			itemType = DELIMITER;
			return true;
		} else if (ch == '"') {
			// Is string
			progIdx++;
			ch = prog[progIdx]; // Skip "
			while (ch != '"' && ch != '\r') { // Get whole string
				item += ch;
				progIdx++;
				ch = prog[progIdx];
			}

			if (ch == '\r') { // end of line instead of second "
				handleErr(MISSQUOTE);
				return false;
			}
			progIdx++; // Skip second "
			itemType = STRING;
			return true;
		} else {

			// Is number or word
			while (progIdx < prog.length && !isDelim(prog[progIdx])) {
				// get entire whatever
				item += prog[progIdx];
				progIdx++;
			}

			if (isNumber(item)) {
				// Is a number
				itemType = NUMBER;
				return true;
			} else if (isBoolean(item)) {
				itemType = BOOLEAN;
				return true;
			}
			{
				// Is bool op, comm, var, funct
				itemType = lookUp(item);
				if (itemType == UNKNCOM)
					itemType = VARIABLE; // New variable
				return true;
			}
		}
	}

	//
	//
	//
	// **************** Background Methods***********************************
	//
	//
	//

	private boolean isDelim(char c) {
		if ((" \r,<>+-/*%^=();".indexOf(c) != -1))
			return true;
		return false;
	}

	// Return true if c is a space or a tab.
	boolean isSpaceOrTab(char c) {
		if (c == ' ' || c == '\t')
			return true;
		return false;
	}

	boolean isRelOp(char c) {
		if (relops.indexOf(c) != -1)
			return true;
		return false;
	}

	boolean isBoolOp(String str) {
		for (int i = 0; i < bOps.length; i++) {
			if (bOps[i].equals(str))
				return true;
		}
		return false;
	}

	boolean isBoolean(Object o) {
		return o.toString().toLowerCase().equals("true")
				|| o.toString().toLowerCase().equals("false");
	}

	boolean toBoolean(Object o) {
		return o.toString().toLowerCase().equals("true");
	}

	boolean isNumber(Object o) {
		String str = o.toString();
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i)) && str.charAt(i) != '-'
					&& str.charAt(i) != '.')
				return false;
		}
		return true;
	}

	private int lookUp(String str) {
		int i;
		// Convert to lowercase.
		str = str.toLowerCase();

		// Variable
		if (vars.containsKey(str)) {
			return VARIABLE;
		}

		// Function
		for (i = 0; i < functs.size(); i++) {
			if (functs.get(i).name.equals(str))
				return FUNCTION;
		}

		// Bool Op
		for (i = 0; i < bOps.length; i++) {
			if (bOps[i].equals(str))
				return DELIMITER;
		}

		// Command
		for (i = 0; i < commTable.length; i++) {
			if (commTable[i].equals(str)) {
				commType = i;
				return COMMAND;
			}
		}
		return UNKNCOM; // unknown keyword
	}

	//
	//
	//
	// ***************************EVALUATOR*******************************
	//
	//
	//

	// Parser entry point.
	private Object evaluate() throws InterpreterException {
		Object result;

		if (item.equals(EOL) || item.equals(EOP))
			handleErr(EXPERR); // no expression present

		// Parse and evaluate the expression.
		result = evalExp1();

		return result;
	}

	// Process operators.
	private Object evalExp1() throws InterpreterException {
		Object result, pResult;
		double l_temp, r_temp;
		boolean lb, rb;
		String ls, rs;
		char op;
		String str;

		// See if first part is expression
		result = evalExp2();

		op = item.charAt(0);
		str = item.toLowerCase();

		while (isRelOp(op) || isBoolOp(str)) {
			nextItem(); // get more stuff
			pResult = evalExp1(); // second expression

			if (isNumber(result)) {
				if (isRelOp(op)) {
					if (isNumber(result)) {
						l_temp = (double) result;
						r_temp = (double) pResult;

						switch (op) { // perform the relational operation
						case '<':
							result = (l_temp < r_temp);
							break;
						case LE:
							result = (l_temp <= r_temp);
							break;
						case '>':
							result = (l_temp > r_temp);
							break;
						case GE:
							result = (l_temp >= r_temp);
							break;
						case EQ:
							result = (l_temp == r_temp);
							break;
						}
					} else {
						handleErr(NOTANUMB);
						result = null;
					}
				}
			} else if (isBoolean(result)) {
				if (isBoolOp(str)) {
					if (isBoolean(result)) {

						lb = (boolean) result;
						rb = (boolean) pResult;
						switch (str) {
						case "and":
							result = (lb && rb);
							break;
						case "or":
							result = (lb || rb);
							break;
						case "xor":
							result = (lb ^ rb);
							break;
						case "xand":
							result = (lb == rb);
							break;
						}
					} else {
						handleErr(NOTABOOL);
						result = null;
					}
				}
			} else {
				if (isRelOp(op)) {
					if (!isNumber(result)) {
						// second expression is String
						rs = (String) pResult;
						ls = (String) result;
						double test = (ls.compareTo(rs));

						switch (op) { // perform the relational operation
						case '<':
							result = test < 0;
							break;
						case LE:
							result = test <= 0;
							break;
						case '>':
							result = test > 0;
							break;
						case GE:
							result = test >= 0;
							break;
						case EQ:
							result = test == 0;
							break;
						}
					} else {
						handleErr(NOTASTR);
						result = null;
					}
				}
			}
			op = item.charAt(0);
			str = item.toLowerCase();
		}

		if (DEBUG_E)
			System.out.println("1: " + result);

		return result;
	}

	// Add or subtract two terms.
	private Object evalExp2() throws InterpreterException {
		char op;
		Object result;
		Object pResult;

		result = evalExp3();

		while ((op = item.charAt(0)) == '+' || op == '-') {
			nextItem(); // get more stuff
			pResult = evalExp3();

			if (isNumber(result)) {// number
				if (isNumber(pResult)) {// also number
					switch (op) {
					case '-':
						result = (double) result - (double) pResult;
						break;
					case '+':
						result = (double) result + (double) pResult;
						break;
					}
				} else {
					handleErr(NOTANUMB);
					return null;
				}
			} else if (!isBoolean(result)) {// string
				if (!isNumber(pResult) && !isBoolean(pResult)) {// also string
					switch (op) {
					case '-':
						handleErr(INVALIDEXP);
					case '+':
						result = (String) result + (String) pResult;
						break;
					}
				} else {
					handleErr(NOTASTR);
					return null;
				}
			}
		}
		if (DEBUG_E)
			System.out.println("2: " + result);
		return result;
	}

	// Multiply or divide two factors.
	private Object evalExp3() throws InterpreterException {
		char op;
		Object result;
		Object partialResult;

		result = evalExp4();

		while ((op = item.charAt(0)) == '*' || op == '/' || op == '%') {
			if (!isNumber(result)) { // must be number or invalid operator
				handleErr(NOTANUMB);
				return null;
			}

			nextItem();
			partialResult = evalExp4();

			if (!isNumber(partialResult)) { // also must be number
				handleErr(NOTANUMB);
				return null;
			}

			switch (op) {
			case '*':
				result = (double) result * (double) partialResult;
				break;
			case '/':
				if ((double) partialResult == 0.0)
					handleErr(DIVBYZERO);
				result = (double) result / (double) partialResult;
				break;
			case '%':
				if ((double) partialResult == 0.0)
					handleErr(DIVBYZERO);
				result = (double) result % (double) partialResult;
				break;
			}
		}
		if (DEBUG_E)
			System.out.println("3: " + result);
		return result;
	}

	// Process an exponent.
	private Object evalExp4() throws InterpreterException {
		Object result;
		Object partialResult;
		double ex;
		double t;

		result = evalExp5();

		if (item.equals("^")) {
			if (!isNumber(result)) { // must be number or invalid operator
				handleErr(NOTANUMB);
				return null;
			}

			nextItem();
			partialResult = evalExp4();

			if (!isNumber(partialResult)) { // must also be number
				handleErr(NOTANUMB);
				return null;
			}

			ex = (double) result;
			if ((double) partialResult == 0.0) {
				result = 1.0;
			} else
				for (t = (double) partialResult - 1; t > 0; t--)
					result = (double) result * ex;
		}
		if (DEBUG_E)
			System.out.println("4: " + result);
		return result;
	}

	// Evaluate a unary + or - and NOT
	private Object evalExp5() throws InterpreterException {
		Object result;
		String op = item;

		if (item.equals("-") || item.toLowerCase().equals(bOps[NOT])) {

			nextItem();
			result = evalExp6();

			if (isNumber(result)) {
				// is number
				if (op.equals("-"))
					result = -(double) result;
				else {
					handleErr(NOTABOOL);
					return null;
				}
			} else if (isBoolean(result)) {
				// is boolean
				if (op.toLowerCase().equals(bOps[NOT]))
					result = !(boolean) result;
				else {
					handleErr(NOTANUMB);
					return null;
				}
			} else {
				// is string
				handleErr(INVALIDEXP);
				return null;
			}
		} else {
			result = evalExp6();
		}
		if (DEBUG_E)
			System.out.println("5: " + result);
		return result;
	}

	// Process a parenthesized expression.
	private Object evalExp6() throws InterpreterException {
		Object result;

		if (item.equals("(")) {
			nextItem();
			result = evalExp2();
			if (!item.equals(")"))
				handleErr(UNBALPARENS);
			nextItem();
			return null;
		} else {
			result = atom();
			nextItem();
		}
		if (DEBUG_E)
			System.out.println("\n6: " + result);
		return result;
	}

	// Get the value of a number or variable.
	private Object atom() throws InterpreterException {
		switch (itemType) {
		case NUMBER:
			try {
				return Double.parseDouble(item);
			} catch (NumberFormatException exc) {
				handleErr(NOTANUMB);
			}
		case VARIABLE:
			return getVarVal(item);
		case BOOLEAN:
			return toBoolean(item);
		case STRING:
			return item;
		}
		handleErr(SYNTAX);
		return null;
	}

	//
	//
	//
	// ***********************EXCEPTIONS****************************
	//
	//
	//

	@SuppressWarnings("serial")
	class InterpreterException extends Exception {

		String errStr;

		public InterpreterException(String str) {
			this.errStr = str;
		}

		public String toString() {
			return errStr;
		}
	}

	// Handle an error.
	private void handleErr(int error) throws InterpreterException {
		String[] err = new String[UNKNOWN + 1];

		err[SYNTAX] = "Syntax Error";
		err[UNBALPARENS] = "(... or ...)";
		err[DIVBYZERO] = "1/0";
		err[EQUALEXPECTED] = "For variable assignment and if statements";
		err[NOTAVAR] = "For vars that have no value: assignments,loops";
		err[NOTABOOL] = "Not a boolean";
		err[NOTANUMB] = "Not a number";
		err[NOTASTR] = " Not a string";
		err[DUPFUNCTION] = "Two functions with same name";
		err[ENDEXPECTED] = "Reaches end of program without end";
		err[THENEXPECTED] = "No then after if";
		err[MISSQUOTE] = "Strings missing a quote";
		err[UNKFUNCTION] = "Unknown function";
		err[INVALIDEXP] = "Invalid Expression";
		err[FILENOTFOUND] = "Can't find file";
		err[INPUTIOERROR] = "Input that fails";
		err[EXPERR] = "For if, while and for";
		err[FILEIOERROR] = "Can't load file";
		err[UNKNOWN] = "Unknown error";

		throw new InterpreterException(err[error] + ": " + progIdx
				+ "\nLine number: " + progLine + "\nItem: " + item
				+ "\nItem Type: " + itemType);
	}
}