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

	boolean debug = true;
	final boolean DEBUG_E = false;

	private void debug(String s) {
		if (debug) {
			if (commands != null) {
				for (int i = 0; i < commands.size(); i++)
					System.out.print("\t");
			}
			System.out.println("> " + s);
			System.out.println();
		}
	}

	private void debug(String[] strs) {
		if (debug) {
			String str = "";
			if (commands != null) {
				for (int i = 0; i < commands.size(); i++)
					str += "\t";
			}
			for (String s : strs)
				System.out.println(str + s);
			System.out.println();
		}
	}

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
	// final int VOID = 9; // Only for function

	// Internal representation of Edit commands
	final int UNKNCOM = 0; // Don't Know
	final int PRINT = 1; // Duh
	final int INPUT = 2; // Ask for...
	final int RETURN = 3; // End function prematurely or return val
	final int THEN = 4; // Expected after If
	final int END = 5; // Ends some commands
	final int DO = 6; // Expected after while
	final int ELSE = 7; // the else to the if

	// End is expected after these
	final int IF = 8; // Start If
	final int FOR = 9; // Start For
	final int WHILE = 10; // Start While
	final int FUNCTION = 11; // Creates new Functions

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
	final int DOEXPECTED = 12;
	final int UNKFUNCTION = 13; // Unknown function
	final int INVALIDEXP = 14; // Invalid Expression
	final int UNEXPITEM = 15;

	final int FILENOTFOUND = 16; // can't find file
	final int INPUTIOERROR = 17; // Input that fails
	final int EXPERR = 18; // for if, while and for
	final int FILEIOERROR = 19; // can't load file

	final int UNKNOWN = 20;

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

	String[] commTable = { "", "print", "input", "return", "then", "end", "do",
			"else", "if", "for", "while", "function" };

	class Command {
		int loc, comm = 0, line;

		public String toString() {
			return commTable[comm];
		}
	}

	class ForLoop extends Command {
		String vName;
		int expLoc, itLoc;

		public ForLoop(String n, int exp, int it, int lo, int lin) {
			comm = FOR;
			vName = n;
			expLoc = exp;
			itLoc = it;
			loc = lo;
			line = lin;
		}

		public ForLoop() {
			comm = FOR;
		}
	}

	class WhileLoop extends Command {
		int expLoc, line;

		public WhileLoop(int exp, int lo, int lin) {
			comm = WHILE;
			expLoc = exp;
			loc = lo;
			line = lin;
		}

		public WhileLoop() {
			comm = WHILE;
		}
	}

	class Function extends Command {
		String name;

		public Function(String n, int c) {
			comm = FUNCTION;
			name = n;
			loc = c;
		}
	}

	class IfStat extends Command {
		boolean done;

		public IfStat(boolean d) {
			comm = IF;
			done = d;
		}
	}

	private Stack<Command> commands; // All loops and commands

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
		debug("Loading program...");

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
	public void run(boolean d) throws InterpreterException {
		debug("Running program...");

		debug = d;

		// Initialize to run a new program
		vars = new TreeMap<String, Object>();
		functs = new ArrayList<Function>();

		commands = new Stack<Command>();

		progIdx = 0;
		progLine = 1;

		// Let's get this started(Runs code)
		runCode();
	}

	// Let's go! (Runs code)
	private void runCode() throws InterpreterException {
		debug("Starting program...");

		// Runs until
		while (nextItem()) {

			// Not exisiting funct or var, so new var
			if (itemType == VARIABLE) {
				assignVar();
			} else { // Start of line, must be command or function
				debug("> Command:" + commType);
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
				case ELSE:
					execElse();
					break;
				}

				debug("> Done with command");
			}

			// All functions end on their line
			if (itemType != EOL && itemType != EOP) {
				handleErr(UNEXPITEM);
			}
		}
	}

	// simple print command
	private void print() throws InterpreterException {
		debug("Print");

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

	// Input stringsn that start with number = bad
	private void input() throws InterpreterException {
		debug("Get Input");
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

		try {
			str = br.readLine();

			vars.put(item, str);

		} catch (IOException e) { // Idk what happend
			handleErr(INPUTIOERROR);
			return;
		}
		nextItem();
	}

	private void execFunction() throws InterpreterException {
		debug("Function");
		// TODO
	}

	private void execReturn() {
		debug("Return");
		// TODO Auto-generated method stub
	}

	private void execIf() throws InterpreterException {
		debug("If statement");
		boolean result;

		nextItem();

		try {
			result = (boolean) evaluate();
		} catch (ClassCastException exc) {
			handleErr(NOTABOOL);
			return;
		}

		commands.push(new IfStat(result));

		if (result) { // Execute the If // Throw away Then
			if (commType != THEN) {
				handleErr(THENEXPECTED);
				return;
			}
			nextItem();

		} else
			nextEnd(); // Skip this If
	}

	private void execElse() throws InterpreterException {
		debug("Else statement");
		Command c = commands.peek();

		if (c.comm != IF) {
			handleErr(SYNTAX);
			return;
		}

		if (!((IfStat) c).done) {
			nextItem();

			if (commType == IF) {

				nextItem();

				boolean result;

				try {
					result = (boolean) evaluate();
				} catch (ClassCastException exc) {
					handleErr(NOTABOOL);
					return;
				}

				if (result) { // Execute the If
					((IfStat) c).done = true;

					if (commType != THEN) {
						handleErr(THENEXPECTED);
						return;
					}
					nextItem();

				} else
					nextEnd(); // Skip this If
				return;
			}
		} else
			nextEnd();
	}

	// for loops
	private void execFor() throws InterpreterException {
		debug("For Loop");
		double i;
		int expLoc, ittLoc, loc;
		String vname;

		nextItem(); // control variable
		vname = item;

		nextItem(); // =
		if (item.equals("=")) {
			nextItem();
			try {
				i = (double) evaluate(); // initial value
			} catch (ClassCastException exc) {
				handleErr(EXPERR);
				return;
			}

			vars.put(vname, i); // add value to map
		}

		// evaluate should end with ,
		if (!item.equals(",")) {
			handleErr(SYNTAX);
			return;
		}

		expLoc = progIdx; // expression would be next
		nextItem();

		// can run once && skip to itt
		try {
			if (!(boolean) evaluate()) {
				commands.push(new ForLoop());
				nextEnd(); // else skip it all
				return;
			}
		} catch (ClassCastException exc) {
			handleErr(NOTABOOL);
		}

		// evaluate ends in ,
		if (!item.equals(",")) {
			handleErr(SYNTAX);
			return;
		}

		ittLoc = progIdx; // itterative would be next
		nextItem();

		evaluate();

		// evaluate ends in do
		if (commType != DO) {
			handleErr(DOEXPECTED);
			return;
		}

		loc = progIdx;
		nextItem(); // should be EOL

		ForLoop newfor;

		try { // end value
			newfor = new ForLoop(vname, expLoc, ittLoc, loc, progLine);
		} catch (ClassCastException exc) {
			handleErr(EXPERR);
			return;
		}

		commands.push(newfor); // add to stack
	}

	private void execWhile() throws InterpreterException {
		debug("While Loop");
		int expLoc;

		expLoc = progIdx;

		nextItem(); // expression

		// can run once
		try {
			if (!(boolean) evaluate()) {
				commands.push(new WhileLoop());
				nextEnd(); // else skip it all
				return;
			}
		} catch (ClassCastException exc) {
			handleErr(NOTABOOL);
		}

		// evaluate ends in do
		if (commType != DO) {
			handleErr(DOEXPECTED);
			return;
		}

		int loc = progIdx;
		nextItem(); // should be EOL

		WhileLoop loop;

		try { // end value
			loop = new WhileLoop(expLoc, loc, progLine);
		} catch (ClassCastException exc) {
			handleErr(EXPERR);
			return;
		}

		commands.push(loop); // add to stack
	}

	private void endComm() throws InterpreterException {
		debug("End Command");
		Command p = commands.peek();
		if (p == null) {
			handleErr(SYNTAX);
		}
		switch (p.comm) {
		case IF:
			nextItem();
			commands.pop();
			return;
		case FOR:
			int loc = progIdx;
			ForLoop f = (ForLoop) p;
			if (f.loc > 0) {
				progIdx = f.itLoc;
				nextItem();
				vars.put(f.vName, (double) evaluate());
				progIdx = f.expLoc;
				nextItem();

				if ((boolean) evaluate()) {
					progIdx = f.loc;
					progLine = f.line;
				} else {
					commands.pop();
					progIdx = loc;
				}
				nextItem();
				return;
			} else {
				commands.pop();
				progIdx = loc;
				nextItem();
				return;
			}
		case WHILE:
			loc = progIdx;
			WhileLoop w = (WhileLoop) p;
			if (w.loc > 0) {
				progIdx = w.expLoc;
				nextItem(); // exp

				if ((boolean) evaluate()) {
					progIdx = w.loc;
					progLine = w.line;
				} else {
					commands.pop();
					progIdx = loc;
				}
				nextItem();
				return;
			} else {
				commands.pop();
				progIdx = loc;
				nextItem();
				return;
			}
		}
	}

	// Get next End, used to skip functions
	private void nextEnd() throws InterpreterException {
		debug("Next end");
		int count = 1;
		while (count > 0 && nextItem()) {
			if (commType > 7) { // Loops and functions and stuff
				count++;
				debug("Find End: " + count);
			}
			if (commType == END) {
				count--;
				debug("Find End: " + count);
			}
			if (commType == ELSE)
				if (count == 1) {
					execElse();
					return;
				} else if (nextItem() && commType == IF) {
					debug("Find End: " + count);
				}
		}

		if (commType != END) {
			handleErr(ENDEXPECTED);
			return;
		}

		endComm();
	}

	//
	//
	//
	// *********************************************NEXTITEM*******************************************
	//
	//
	//

	// Obtain next item, returns false if EOP
	private boolean nextItem() throws InterpreterException {
		boolean result = getNext();
		debug(new String[] { "Item: " + item, "CommStack: " + commands });
		// "ItemType: " + itemType, "CommType: " + commType,

		// try {
		// if (debug)
		// Thread.sleep(500);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }

		return result;
	}

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
			item = " ";
			return false;
		}

		// Check for end of line
		if (prog[progIdx] == '\r') {
			progIdx += 2;
			itemType = EOL;
			item = " ";
			progLine++;
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
	// ***************************EVALUATOR*******************************
	//
	//
	//

	// Parser entry point.
	private Object evaluate() throws InterpreterException {
		debug("Evaluate");
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

			if (isNumber(result)) {
				pResult = evalExp2(); // second expression
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
				pResult = evalExp1();
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
					pResult = evalExp2();
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
			debug("1: " + result);

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
			debug("2: " + result);
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
			debug("3: " + result);
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
			debug("4: " + result);
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
			debug("5: " + result);
		return result;
	}

	// Process a parenthesized expression.
	private Object evalExp6() throws InterpreterException {
		Object result;

		if (item.equals("(")) {
			nextItem();
			result = evalExp1();
			if (!item.equals(")"))
				handleErr(UNBALPARENS);
			nextItem();
			return result;
		} else {
			result = atom();
			nextItem();
		}
		if (DEBUG_E)
			debug("6: " + result);
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
			Object o = getVarVal(item);
			if (isNumber(o)) {
				if (DEBUG_E)
					debug("atom: " + o.toString());
				return Double.parseDouble(o.toString());
			}
			if (isBoolean(o))
				return toBoolean((String) o);
			return o;
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

	private void assignVar() throws InterpreterException {
		debug("Assign variable");
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

		debug("Get var: " + vars.get(vname));
		return vars.get(vname); // return Object
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
		err[EQUALEXPECTED] = "Equal Expected";
		err[NOTAVAR] = "For vars that have no value: assignments,loops";
		err[NOTABOOL] = "Not a boolean";
		err[NOTANUMB] = "Not a number";
		err[NOTASTR] = " Not a string";
		err[DUPFUNCTION] = "Two functions with same name";
		err[ENDEXPECTED] = "Reaches end of program without end";
		err[THENEXPECTED] = "No then after if";
		err[DOEXPECTED] = "No then after if";
		err[MISSQUOTE] = "Strings missing a quote";
		err[UNKFUNCTION] = "Unknown function";
		err[INVALIDEXP] = "Invalid Expression";
		err[UNEXPITEM] = "Unexpeced Item";
		err[FILENOTFOUND] = "Can't find file";
		err[INPUTIOERROR] = "Input that fails";
		err[EXPERR] = "For if, while and for";
		err[FILEIOERROR] = "Can't load file";
		err[UNKNOWN] = "Unknown error";

		throw new InterpreterException(err[error] + ": " + progIdx
				+ "\nLine number: " + progLine + "\nItem: " + item
				+ "\nItem Type: " + itemType + "\ncommType: " + commType);
	}
}