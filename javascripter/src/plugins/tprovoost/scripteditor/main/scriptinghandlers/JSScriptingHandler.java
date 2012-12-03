package plugins.tprovoost.scripteditor.main.scriptinghandlers;

import icy.image.IcyBufferedImage;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginInstaller;
import icy.plugin.PluginRepositoryLoader;
import icy.sequence.Sequence;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.compiler.ast.NewExpr;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.ParameterizedCompletion.Parameter;
import org.fife.ui.autocomplete.VariableCompletion;
import org.fife.ui.rtextarea.Gutter;

import plugins.tprovoost.scriptenginehandler.ScriptEngineHandler;
import sun.org.mozilla.javascript.internal.CompilerEnvirons;
import sun.org.mozilla.javascript.internal.Context;
import sun.org.mozilla.javascript.internal.Node;
import sun.org.mozilla.javascript.internal.Parser;
import sun.org.mozilla.javascript.internal.Token;
import sun.org.mozilla.javascript.internal.ast.Assignment;
import sun.org.mozilla.javascript.internal.ast.AstNode;
import sun.org.mozilla.javascript.internal.ast.AstRoot;
import sun.org.mozilla.javascript.internal.ast.ExpressionStatement;
import sun.org.mozilla.javascript.internal.ast.FunctionCall;
import sun.org.mozilla.javascript.internal.ast.FunctionNode;
import sun.org.mozilla.javascript.internal.ast.Name;
import sun.org.mozilla.javascript.internal.ast.NewExpression;

public class JSScriptingHandler extends ScriptingHandler {

    private int commandStartOffset;
    private int commandEndOffset;

    public JSScriptingHandler(DefaultCompletionProvider provider, JTextComponent textArea, Gutter gutter) {
	super(provider, "javascript", textArea, gutter);
    }

    @Override
    public void installDefaultLanguageCompletions(String language) throws ScriptException {

	ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);
	HashMap<String, Class<?>> engineFunctions = engineHandler.getEngineFunctions();

	// IMPORT PACKAGES
	importJavaScriptPackages(engine);

	// IMPORT A FEW IMPORTANT SEQUENCES, TO BE REMOVED
	FunctionCompletion c;
	// ArrayList<Parameter> params = new ArrayList<Parameter>();
	engine.eval("function getSequence() { return Icy.getMainInterface().getFocusedSequence() }");
	c = new FunctionCompletion(provider, "getSequence", "Sequence");
	c.setDefinedIn("MainInterface");
	c.setReturnValueDescription("The focused sequence is returned.");
	c.setShortDescription("Returns the sequence under focus. Returns null if no sequence opened.");
	provider.addCompletion(c);
	engineFunctions.put("getSequence", Sequence.class);

	engine.eval("function getImage() { return Icy.getMainInterface().getFocusedImage(); }");
	c = new FunctionCompletion(provider, "getImage", "IcyBufferedImage");
	c.setDefinedIn("MainInterface");
	c.setShortDescription("Returns the current image viewed in the focused sequence.");
	c.setReturnValueDescription("Returns the focused Image, returns null if no sequence opened");
	provider.addCompletion(c);
	engineFunctions.put("getImage", IcyBufferedImage.class);

	// ADD JS FUNCTIONS
	engineFunctions.put("importClass", void.class);
	engineFunctions.put("importPackage", void.class);

	// IMPORT PLUGINS FUNCTIONS
	importFunctions();

    }

    public void importJavaScriptPackages(ScriptEngine engine) throws ScriptException {
	// icy important packages
	engine.eval("importPackage(Packages.icy.main)\n" + "importPackage(Packages.icy.plugin)\n" + "importPackage(Packages.icy.sequence)\n"
		+ "importPackage(Packages.icy.image)\n" + "importPackage(Packages.icy.file)\n" + "importPackage(Packages.icy.file.xls)");

	ArrayList<String> engineDeclaredImports = ScriptEngineHandler.getEngineHandler(engine).getEngineDeclaredImports();
	if (!engineDeclaredImports.contains("icy.main"))
	    engineDeclaredImports.add("icy.main");
	if (!engineDeclaredImports.contains("icy.plugin"))
	    engineDeclaredImports.add("icy.plugin");
	if (!engineDeclaredImports.contains("icy.sequence"))
	    engineDeclaredImports.add("icy.sequence");
	if (!engineDeclaredImports.contains("icy.image"))
	    engineDeclaredImports.add("icy.image");
	if (!engineDeclaredImports.contains("icy.file"))
	    engineDeclaredImports.add("icy.file");
	if (!engineDeclaredImports.contains("icy.file.xls"))
	    engineDeclaredImports.add("icy.file.xls");
    }

    @Override
    public void registerImports() {
	String s = textArea.getText();
	Pattern patternClasses = Pattern.compile("importClass\\((Packages\\.|)((\\w|\\.)+)\\)");
	Matcher m = patternClasses.matcher(s);
	int offset = 0;
	while (m.find(offset)) {
	    String foundString = m.group(0);
	    String imported = m.group(2);
	    scriptDeclaredImportClasses.add(imported);
	    int idxString = s.indexOf(foundString, offset);
	    if (idxString == -1)
		break;
	    offset = idxString + foundString.length();
	}

	Pattern patternPackages = Pattern.compile("importPackage\\((Packages\\.|)((\\w|\\.)+)\\)");
	m = patternPackages.matcher(s);
	offset = 0;
	while (m.find(offset)) {
	    String foundString = m.group(0);
	    String imported = m.group(2);
	    scriptDeclaredImports.add(imported);
	    int idxString = s.indexOf(foundString, offset);
	    if (idxString == -1)
		break;
	    offset = idxString + foundString.length();
	}
    }

    @Override
    public void autoDownloadPlugins() {
	String s = textArea.getText();
	Pattern patternClasses = Pattern.compile("importClass\\((Packages\\.|)((\\w|\\.)+)\\)");
	Matcher m = patternClasses.matcher(s);
	int offset = 0;
	while (m.find(offset)) {
	    String foundString = m.group(0);
	    String imported = m.group(2);
	    for (PluginDescriptor pd : PluginRepositoryLoader.getPlugins()) {
		if (pd.getClassName().contentEquals(imported) && !pd.isInstalled())
		    PluginInstaller.install(pd, false);
	    }
	    int idxString = s.indexOf(foundString, offset);
	    if (idxString == -1)
		break;
	    offset = idxString + foundString.length();
	}

	Pattern patternPackages = Pattern.compile("importPackage\\((Packages\\.|)((\\w|\\.)+)\\)");
	m = patternPackages.matcher(s);
	offset = 0;
	while (m.find(offset)) {
	    String foundString = m.group(0);
	    String imported = m.group(2);

	    for (PluginDescriptor pd : PluginRepositoryLoader.getPlugins()) {
		if (pd.getClassName().startsWith(imported) && !pd.isInstalled())
		    PluginInstaller.install(pd, false);
	    }

	    int idxString = s.indexOf(foundString, offset);
	    if (idxString == -1)
		break;
	    offset = idxString + foundString.length();
	}
    }

    @Override
    protected void detectVariables(String s, Context context) throws ScriptException {
	final CompilerEnvirons comp = new CompilerEnvirons();
	comp.initFromContext(context);
	final Parser parser = new Parser(comp, comp.getErrorReporter());
	AstRoot root;
	root = parser.parse(s, "", 1);

	if (root == null || !root.hasChildren())
	    return;
	// no issue, removes variables
	for (Completion c : variableCompletions)
	    provider.removeCompletion(c);
	variableCompletions.clear();
	if (DEBUG)
	    dumpTree(root, root, 1, "");

	//
	// initialize offset values
	//
	commandStartOffset = 0;
	char charvalue;
	while (commandStartOffset < s.length() && ((charvalue = s.charAt(commandStartOffset)) == '\n' || charvalue == ' ' || charvalue == '\t'))
	    commandStartOffset++;

	int idxln = s.indexOf('\n', commandStartOffset);
	int idxSemiColon = s.indexOf(';', commandStartOffset);
	commandEndOffset = -1;

	// choose the best ending character
	if (idxln == -1)
	    commandEndOffset = idxSemiColon;
	else if (idxSemiColon == -1)
	    commandEndOffset = idxln;
	else
	    commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;

	// in case issue
	if (commandEndOffset == -1)
	    commandEndOffset = s.length();
	else
	    commandEndOffset++;

	// start variable registration
	registerVariables(s, root, root);

	// add the completions
	provider.addCompletions(variableCompletions);
    }

    /**
     * Dump the whole tree in the cconsole.
     * 
     * @param n
     * @param root
     * @param commandIdx
     * @param decal
     */
    private void dumpTree(Node n, AstRoot root, int commandIdx, String decal) {
	System.out.print(commandIdx + ": " + decal + typeToName(n.getType()));
	switch (n.getType()) {
	case Token.BINDNAME:
	    // System.out.println("bindname");
	case Token.STRING:
	case Token.NAME:
	    System.out.println(": " + n.getString());
	    break;
	case Token.NUMBER:
	    System.out.println(": " + n.getDouble());
	    break;
	// case Token.CALL:
	// System.out.println("call");
	// break;
	case Token.FUNCTION:
	    int fnIndex = n.getExistingIntProp(Node.FUNCTION_PROP);
	    FunctionNode fn = root.getFunctionNode(fnIndex);
	    if (fn.getFunctionType() != FunctionNode.FUNCTION_EXPRESSION) {
		System.out.println("not an expression.");
	    }
	    Name funcName = fn.getFunctionName();
	    System.out.print(": " + funcName + " ");
	    for (int i = 0; i < fn.getParamCount(); ++i) {
		String s = fn.getParamOrVarName(i);
		if (s == null || s == "" || s.contentEquals(funcName.getString()))
		    continue;
		System.out.print(s + " ");
	    }
	    n = fn;
	    System.out.println();
	    break;
	default:
	    System.out.println();
	}
	if (n.hasChildren()) {
	    Node child = n.getFirstChild();
	    while (child != null) {
		dumpTree(child, root, commandIdx, "-" + decal);
		child = child.getNext();
		if (n == root) {
		    ++commandIdx;
		    System.out.println();
		}
	    }
	}
    }

    /**
     * Register all variables in the successfully compiled script.
     * 
     * @param n
     * @param root
     * @throws ScriptException
     */
    private void registerVariables(String text, Node n, AstRoot root) throws ScriptException {
	if (DEBUG)
	    System.out.println(typeToName(n.getType()) + " " + commandStartOffset + "/" + commandEndOffset);
	// register current
	Completion c = generateCompletion(n, root, text);
	if (c != null) {
	    boolean alreadyExists = false;
	    for (int i = 0; i < variableCompletions.size() && !alreadyExists; ++i) {
		if (variableCompletions.get(i).compareTo(c) == 0) {
		    if (textArea.getCaret().getDot() > commandStartOffset)
			variableCompletions.remove(i);
		    else
			alreadyExists = true;
		}
	    }
	    if (!alreadyExists)
		variableCompletions.add(c);
	}
	// recursive call on children (if any)
	if (n.hasChildren()) {
	    Node child = n.getFirstChild();
	    while (child != null) {
		registerVariables(text, child, root);
		child = child.getNext();
		if (n == root) {
		    // TODO move from the if...
		    commandStartOffset = commandEndOffset;
		    char ch;
		    // while(Pattern.matches("\\s+.*",
		    // text.substring(commandStartOffset))) {
		    // commandStartOffset++;
		    // }
		    while (commandStartOffset < text.length() && ((ch = text.charAt(commandStartOffset)) == '_' || !Character.isLetterOrDigit(ch)))
			commandStartOffset++;
		    int idxln = text.indexOf('\n', commandStartOffset);
		    int idxSemiColon = text.indexOf(';', commandStartOffset);
		    if (idxln == -1)
			commandEndOffset = idxSemiColon;
		    else if (idxSemiColon == -1)
			commandEndOffset = idxln;
		    else
			commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;
		    if (commandEndOffset == -1)
			commandEndOffset = commandStartOffset + text.substring(commandStartOffset).length();
		    else
			commandEndOffset += 1;
		}
	    }
	}
    }

    /**
     * Automatically generates the completion for javascript: variable or
     * function.
     * 
     * @param n
     * @param root
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     * @throws ScriptException
     */
    private Completion generateCompletion(Node n, AstRoot root, String text) throws ScriptException {
	switch (n.getType()) {
	case Token.VAR:
	    if (n.getFirstChild() != null && n.getFirstChild().getType() == Token.NAME) {
		commandStartOffset += "var".length();
		Node resNode = n.getFirstChild().getFirstChild();
		Class<?> type = getVariableType(resNode, text);
		String typeStr = type == null ? "" : type.getName();
		VariableCompletion c = new VariableCompletion(provider, n.getFirstChild().getString(), typeStr);
		c.setSummary("variable");
		c.setDefinedIn(fileName);
		c.setRelevance(RELEVANCE_HIGH);
		int start;
		String textLine = text.substring(commandStartOffset, commandEndOffset);
		Pattern p = Pattern.compile("(var\\s+|)\\s*\\w+\\s=\\s((\\w|\\.|\\(|\\)|,|\")+)");
		Matcher m = p.matcher(textLine);
		if (m.find())
		    start = textLine.indexOf(m.group(2));
		else
		    start = commandStartOffset;
		if (type != null)
		    addVariableDeclaration(c.getName(), type, commandStartOffset);
		else
		    addVariableDeclaration(c.getName(), getRealType(resNode, text), start);
		return c;
	    }
	    break;
	case Token.SETNAME: {
	    Node resNode = n.getFirstChild().getNext();
	    Class<?> type = getVariableType(resNode, text);
	    String typeStr = type == null ? "" : type.getName();
	    VariableCompletion c = new VariableCompletion(provider, n.getFirstChild().getString(), typeStr);
	    c.setSummary("Variable");
	    c.setDefinedIn(fileName);
	    c.setRelevance(RELEVANCE_HIGH);
	    addVariableDeclaration(c.getName(), type, commandStartOffset);
	    return c;
	}
	case Token.FUNCTION: {
	    // String type = getVariableTypeAsString(n, text,
	    // commandStartOffset, commandEndOffset);
	    int fnIndex = n.getExistingIntProp(Node.FUNCTION_PROP);
	    FunctionNode fn = root.getFunctionNode(fnIndex);
	    Name funcName = fn.getFunctionName();
	    ArrayList<Parameter> params = new ArrayList<Parameter>();
	    for (int i = 0; i < fn.getParamAndVarCount(); ++i) {
		String s = fn.getParamOrVarName(i);
		if (s == null || s == "" || s.contentEquals(funcName.getString()))
		    continue;
		params.add(new Parameter("", s));
	    }
	    FunctionCompletion fc = new FunctionCompletion(provider, funcName.getString(), "");
	    fc.setParams(params);
	    fc.setRelevance(RELEVANCE_HIGH);
	    return fc;
	}
	case Token.IF:
	case Token.IFEQ:
	case Token.IFNE:
	case Token.SWITCH:
	case Token.TRY:
	case Token.CATCH:
	case Token.WITH:
	case Token.FOR:
	case Token.ELSE:
	case Token.DO:
	case Token.FINALLY:
	case Token.WHILE: {
	    commandStartOffset = commandEndOffset;
	    char ch;
	    // while(Pattern.matches("\\s+.*",
	    // text.substring(commandStartOffset))) {
	    // commandStartOffset++;
	    // }
	    while (commandStartOffset < text.length() && ((ch = text.charAt(commandStartOffset)) == '_' || !Character.isLetterOrDigit(ch)))
		commandStartOffset++;
	    int idxln = text.indexOf('\n', commandStartOffset);
	    int idxSemiColon = text.indexOf(';', commandStartOffset);
	    if (idxln == -1)
		commandEndOffset = idxSemiColon;
	    else if (idxSemiColon == -1)
		commandEndOffset = idxln;
	    else
		commandEndOffset = idxln < idxSemiColon ? idxln : idxSemiColon;
	    if (commandEndOffset == -1)
		commandEndOffset = commandStartOffset + text.substring(commandStartOffset).length();
	    else
		commandEndOffset += 1;
	}
	    break;

	case Token.EXPR_RESULT:
	    // AstNode expr = ((ExpressionStatement) n).getExpression();
	    // if (expr instanceof FunctionCall) {
	    // FunctionCall fc = (FunctionCall) expr;
	    // System.out.println("Function call:" + fc.getTarget().getString()
	    // + ".");
	    // } else if (expr instanceof Assignment) {
	    // Assignment as = (Assignment)expr;
	    // AstNode node = as.getRight();
	    // if (node instanceof NewExpression) {
	    // System.out.println("New: " + ((NewExpression)
	    // node).getTarget().getString());
	    // }
	    // }
	    break;

	// case Token.WITH:
	// break;
	case Token.CALL:
	    resolveCallType(n, text, true);
	    break;
	default:
	    break;
	}
	return null;
    }

    protected void addVariableDeclaration(String name, Class<?> type, int offset) {
	TreeMap<Integer, Class<?>> list = localVariables.get(name);
	if (list == null)
	    list = new TreeMap<Integer, Class<?>>();
	list.put(offset, type);
	localVariables.put(name, list);
    }

    private Class<?> getVariableType(Node n, String text) throws ScriptException {
	if (n == null)
	    return null;
	switch (n.getType()) {
	case Token.NUMBER:
	    return Number.class;
	case Token.STRING:
	    return String.class;
	case Token.TRUE:
	case Token.FALSE:
	    return boolean.class;
	case Token.CALL: {
	    return resolveCallType(n, text, false);
	}
	case Token.FUNCTION: {
	    return void.class;
	}
	case Token.NEW: {
	    return resolveNewType(n.getFirstChild(), text);
	}
	case Token.GETPROP:
	    if (n.getFirstChild() != null && n.getFirstChild().getType() == Token.CALL)
		return resolveCallType(n.getFirstChild(), text, false);
	    return null;
	default:
	    return null;
	}
    }

    /**
     * 
     * @param node
     *            : first child of node "NEW"
     * @param commandEndOffset
     * @param commandStartOffset
     * @return
     */
    private Class<?> resolveNewType(Node n, String text) throws ScriptException {
	String type = buildNew(n, commandStartOffset, commandEndOffset);
	Class<?> clazz = resolveClassDeclaration(type, true);
	if (clazz == null)
	    throw new ScriptException("Type: " + type + " is undefined. Please check imports.", null, findLineContaining(text));
	return clazz;
    }

    @Override
    public Class<?> resolveClassDeclaration(String type, boolean strict) {
	// try absolute
	try {
	    if (type.startsWith("Packages."))
		type = type.substring("Packages.".length());
	    return Class.forName(type);
	} catch (ClassNotFoundException e) {
	}
	return super.resolveClassDeclaration(type, strict);
    }

    private String buildNew(Node n, int commandStartOffset, int commandEndOffset) {
	String newCall = "";
	Node currentChild = n;
	while (currentChild != null) {
	    switch (currentChild.getType()) {
	    case Token.GETPROP:
		newCall = "." + currentChild.getLastChild().getString() + newCall;
		currentChild = currentChild.getFirstChild();
		break;
	    case Token.NAME:
		newCall = "." + currentChild.getString() + newCall;
		currentChild = null;
		break;
	    default:
		newCall = "." + currentChild.getString() + newCall;
		currentChild = null;
		break;
	    }
	}
	// removes the first dot
	if (newCall.startsWith("."))
	    newCall = newCall.substring(1);
	if (newCall.startsWith("Packages."))
	    newCall = newCall.substring("Packages.".length());

	return newCall;
    }

    /**
     * Represents
     * 
     * @param n
     * @param commandEndOffset
     * @param commandStartOffset
     * @return
     * @throws ScriptException
     */
    private Class<?> resolveCallType(Node n, String text, boolean noerror) throws ScriptException {
	if (n.getFirstChild() != null && n.getFirstChild().getType() == Token.GETPROP) {

	    String s = buildFunction(n, text);
	    boolean containsNew = s.contains("new ");
	    if (containsNew) {
		s = s.substring("new ".length());
	    }

	    // create a regex pattern
	    Pattern p = Pattern.compile("\\w(\\w|\\.)*\\((\\w|\\.|,)*\\)");
	    Matcher match = p.matcher(s);

	    int idxP1 = 0;
	    int idxP2;
	    int decal = 0;
	    if (match.find(0)) {
		String firstCall = match.group(0);
		try {
		    int idx = text.indexOf(firstCall.substring(0, idxP1), commandStartOffset);
		    if (idx != -1)
			commandStartOffset = idx;
		    idxP1 = firstCall.indexOf('(');
		    idxP2 = firstCall.indexOf(')');
		    decal += idxP2 + 1;
		    int lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
		    if (lastDot < 0) {
			lastDot = idxP1; // in case of new for instance.
		    }
		    Class<?> clazz = null;

		    // get the className (or binding function name if it is the
		    // case)
		    String classNameOrFunctionNameOrVariable = firstCall.substring(0, lastDot);

		    // get the arguments
		    String argsString = firstCall.substring(idxP1 + 1, idxP2);

		    // separate arguments
		    String[] args = argsString.split(",");

		    // it is a variable
		    clazz = getVariableDeclaration(classNameOrFunctionNameOrVariable);
		    ScriptEngineHandler engineHandler = ScriptEngineHandler.getEngineHandler(engine);

		    // an engine variable
		    if (clazz == null)
			clazz = engineHandler.getEngineVariables().get(classNameOrFunctionNameOrVariable);
		    if (clazz == null)
			// class
			clazz = resolveClassDeclaration(classNameOrFunctionNameOrVariable, true);
		    if (clazz == null)
			throw new ScriptException("Unknown class: " + classNameOrFunctionNameOrVariable, null, findLineContaining(text));

		    // generate the Class<?> arguments
		    Class<?> clazzes[];
		    if (argsString.isEmpty()) {
			clazzes = new Class<?>[0];
		    } else {
			clazzes = new Class<?>[args.length];
			for (int i = 0; i < clazzes.length; ++i)
			    clazzes[i] = Class.forName(args[i]);
			clazzes = getGenericNumberTypes(text, clazz, firstCall.substring(lastDot + 1, idxP1), clazzes);
		    }

		    // the first type !
		    Class<?> returnType;
		    if (containsNew) {
			returnType = clazz;
		    } else {
			Method m = clazz.getMethod(firstCall.substring(lastDot + 1, idxP1), clazzes);
			returnType = m.getReturnType();
		    }

		    if (DEBUG)
			System.out.println("function created: " + commandStartOffset + " " + text.substring(commandStartOffset));
		    IcyFunctionBlock fb = new IcyFunctionBlock(firstCall.substring(lastDot + 1, idxP1), commandStartOffset, returnType);
		    blockFunctions.put(commandStartOffset, fb);

		    // iterate over the next functions, based on the returnType
		    while (match.find(decal) && !(firstCall = match.group()).isEmpty()) {
			if (returnType == void.class)
			    throw new ScriptException("Void return, impossible to call something else on it.", null, findLineContaining(text));
			idxP1 = firstCall.indexOf('(');
			idxP2 = firstCall.indexOf(')');
			decal += idxP2 + 2; // account for ) and .
			argsString = firstCall.substring(idxP1 + 1, idxP2);
			args = argsString.split(",");
			if (argsString.isEmpty()) {
			    clazzes = new Class<?>[0];
			} else {
			    clazzes = new Class<?>[args.length];
			    for (int i = 0; i < clazzes.length; ++i)
				clazzes[i] = Class.forName(args[i]);
			    lastDot = firstCall.substring(0, idxP1).lastIndexOf('.');
			    if (lastDot < 0) {
				lastDot = -1; // in case of new for instance.
			    }
			    clazzes = getGenericNumberTypes(text, returnType, firstCall.substring(lastDot + 1, idxP1), clazzes);
			}
			Method m = returnType.getMethod(firstCall.substring(0, idxP1), clazzes);
			returnType = m.getReturnType();
			if (DEBUG)
			    System.out.println("function created: " + commandStartOffset + text.substring(commandStartOffset));
			fb = new IcyFunctionBlock(firstCall.substring(lastDot + 1, idxP1), commandStartOffset, returnType);
			blockFunctions.put(commandStartOffset, fb);
		    }
		    return returnType;
		} catch (ClassNotFoundException e) {
		    throw new ScriptException("Class Not Found: " + e.getLocalizedMessage(), null, findLineContaining(text));
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
		    throw new ScriptException("No such method: " + e.getLocalizedMessage(), null, findLineContaining(text));
		}
	    }
	    return null;
	} else {
	    // direct call: binding
	    Node firstChild = n.getFirstChild();

	    // args are calculated but unused as of now.
	    String args = "(";
	    Node nextChild = firstChild.getNext();
	    int cptAdded = 0;
	    while (nextChild != null) {
		if (cptAdded > 0)
		    args += ',';
		Class<?> type = getRealType(nextChild, text);
		if (type == null)
		    args += "unknown";
		else
		    args += type;
		nextChild = nextChild.getNext();
		cptAdded++;
	    }
	    args += ')';
	    Class<?> res = localFunctions.get(firstChild.getString() + args);
	    if (res == null)
		res = ScriptEngineHandler.getEngineHandler(engine).getEngineFunctions().get(firstChild.getString()); // +
														     // args);
	    if (res == null && !noerror)
		throw new ScriptException(firstChild.getString() + args + " does not exist.", null, findLineContaining(text));
	    return res;
	}
    }

    /**
     * Returns the line containg the string. Be careful, the first line has
     * index 1.
     * 
     * @param string
     * @return
     */
    private int findLineContaining(String text) {
	if (textArea instanceof JTextArea) {
	    if (text != null) {
		JTextArea tx = new JTextArea(text);
		try {
		    return tx.getLineOfOffset(commandStartOffset) + 1;
		} catch (BadLocationException e) {
		}
	    }
	    return -1;
	} else
	    return 1;
    }

    /**
     * FIXME : issue with same name functions, will always use the first one.
     * 
     * @param clazz
     * 
     * @param function
     * @param argsClazzes
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     */
    private Class<?>[] getGenericNumberTypes(String s, Class<?> clazz, String function, Class<?>[] argsClazzes) {
	Class<?>[] toReturn = new Class<?>[argsClazzes.length];
	String fullCommand;
	if (commandStartOffset >= 0 && commandStartOffset < s.length() && commandEndOffset >= 0 && commandEndOffset <= s.length())
	    fullCommand = s.substring(commandStartOffset, commandEndOffset);
	else
	    return null;
	int idxStart = fullCommand.indexOf(function);

	if (idxStart == -1)
	    return argsClazzes;

	int idxP1 = fullCommand.indexOf('(', idxStart);
	int idxP2 = fullCommand.indexOf(')', idxStart);

	if (idxP1 == -1 || idxP2 == -1)
	    return argsClazzes;

	String argumentsChained = fullCommand.substring(idxP1 + 1, idxP2);
	String[] args = argumentsChained.split(",");

	if (args.length != argsClazzes.length)
	    return argsClazzes;

	// FIXME
	boolean hasNumber = false;
	for (int i = 0; i < argsClazzes.length; ++i) {
	    if (argsClazzes[i] == Number.class)
		hasNumber = true;
	    toReturn[i] = argsClazzes[i];
	}
	if (hasNumber) {
	    for (Method m : clazz.getMethods()) {
		if (m.getName().contentEquals(function)) {
		    Class<?> params[] = m.getParameterTypes();
		    boolean ok = true;
		    if (params.length == argsClazzes.length) {
			for (int i = 0; i < params.length && ok; ++i) {
			    if (params[i].isAssignableFrom(argsClazzes[i]))
				toReturn[i] = params[i];
			    else if (params[i].isPrimitive()) {
				if ((params[i] == float.class || params[i] == double.class) && args[i].contains("."))
				    toReturn[i] = params[i];
				else if (!(params[i] == float.class || params[i] == double.class) && !args[i].contains("."))
				    toReturn[i] = params[i];
				else
				    break;
			    } else
				break;
			}
		    }
		}
	    }
	    return toReturn;
	} else
	    return toReturn;
    }

    private String buildFunction(Node n, String text) throws ScriptException {
	String callName = "";
	String newType = "";
	boolean functionNext = true;
	Node currentChild = n;
	while (currentChild != null) {
	    if (currentChild.getType() == Token.GETPROP) {
		String s = currentChild.getLastChild().getString();
		if (functionNext) {
		    s += '(';
		    Node nextChild = currentChild.getNext();
		    int cptAdded = 0;
		    while (nextChild != null) {
			if (cptAdded > 0)
			    s += ',';
			Class<?> result = getRealType(nextChild, text);
			if (result == null)
			    throw new ScriptException("unknown type.");
			s += result.getName();
			nextChild = nextChild.getNext();
			cptAdded++;
		    }
		    s += ')';
		    functionNext = false;
		}
		callName = "." + s + callName;
		currentChild = currentChild.getFirstChild();
	    } else if (currentChild.getType() == Token.CALL) {
		functionNext = true;
		currentChild = currentChild.getFirstChild();
	    } else if (currentChild.getType() == Token.NEW) {
		newType = currentChild.getFirstChild().getString() + "(";
		// arguments iteration:
		Node nextChild = currentChild.getNext();
		int cptAdded = 0;
		while (nextChild != null) {
		    if (cptAdded > 0)
			newType += ',';
		    Class<?> result = getRealType(nextChild, text);
		    if (result == null)
			throw new ScriptException("unknown type.");
		    newType += result.getName();
		    nextChild = nextChild.getNext();
		    cptAdded++;
		}
		newType += ")";
		currentChild = null;
	    } else {
		if (functionNext)
		    callName = "." + currentChild.getString() + "()" + callName;
		else
		    callName = "." + currentChild.getString() + callName;
		currentChild = currentChild.getFirstChild();
	    }
	}
	// removes the last dot
	callName = callName.substring(1);

	// removes eventual "Packages." String
	if (callName.startsWith("Packages.") || callName.startsWith("packages."))
	    callName = callName.substring("Packages.".length());

	if (!newType.isEmpty())
	    callName = "new " + newType + "." + callName;

	return callName;
    }

    /**
     * Get the type of a variable definition.
     * 
     * @param n
     * @param commandStartOffset
     * @param commandEndOffset
     * @return
     * @throws ScriptException
     */
    public Class<?> getRealType(Node n, String text) throws ScriptException {
	switch (n.getType()) {
	case Token.NUMBER:
	    return Number.class;
	case Token.STRING:
	    return String.class;
	case Token.TRUE:
	case Token.FALSE:
	    return boolean.class;
	case Token.NAME:
	    return getVariableDeclaration(n.getString());
	case Token.CALL:
	    Class<?> res = resolveCallType(n, text, false);
	    return res;
	case Token.GETPROP:
	    return String.class;
	}

	return null;
    }

    @Override
    protected void organizeImports(JTextComponent tc) {
	organizeImportsStatic(tc);
    }

    /**
     * Static because of the call in the autocomplete.
     * 
     * @param tc
     */
    public static void organizeImportsStatic(JTextComponent tc) {
	ArrayList<String> listImportsClass = new ArrayList<String>();
	ArrayList<String> listImportsPackages = new ArrayList<String>();
	boolean errorHappened = false;

	String originalText = tc.getText();
	String text = "";
	while ((text = tc.getText()).contains("importClass(") && !errorHappened) {
	    int idxStart = text.indexOf("importClass(");
	    if (idxStart == -1) // should never happen because of the contains
		continue;
	    int idxStop = text.indexOf(')', idxStart);
	    if (idxStop == -1) { // something weird happened in the code, stop.
		errorHappened = true;
		break;
	    }
	    Caret c = tc.getCaret();
	    c.setDot(idxStart);
	    c.moveDot(idxStop + 1);
	    listImportsClass.add(tc.getSelectedText());
	    tc.replaceSelection("");
	}
	while ((text = tc.getText()).contains("importPackage(") && !errorHappened) {
	    int idxStart = text.indexOf("importPackage(");
	    if (idxStart == -1) // should never happen because of the contains
		continue;
	    int idxStop = text.indexOf(')', idxStart);
	    if (idxStop == -1) { // something weird happened in the code, stop.
		errorHappened = true;
		break;
	    }
	    Caret c = tc.getCaret();
	    c.setDot(idxStart);
	    c.moveDot(idxStop + 1);
	    listImportsPackages.add(tc.getSelectedText());
	    tc.replaceSelection("");
	}
	if (errorHappened) {
	    tc.setText(originalText);
	} else {
	    String result = "";
	    Collections.sort(listImportsClass);
	    Collections.sort(listImportsPackages);

	    for (int i = 0; i < listImportsClass.size(); ++i) {
		if (i == 0)
		    result += listImportsClass.get(i);
		else
		    result += "\n" + listImportsClass.get(i);
	    }
	    for (int i = 0; i < listImportsPackages.size(); ++i) {
		if (i == 0)
		    result += "\n\n" + listImportsPackages.get(i);
		else
		    result += "\n" + listImportsPackages.get(i);
	    }
	    String leftText = tc.getText();
	    char c;
	    while (leftText.length() > 0 && ((c = leftText.charAt(0)) == ' ' || c == '\n' || c == '\t'))
		leftText = leftText.substring(1);
	    tc.setText(result + "\n\n" + leftText);
	}
    }

    /**
     * Given the fact that the text can be different from {@link #textArea}
     * .getText(), this method returns the offset in the original text,
     * corresponding to the commandStartOffset in the modified text.
     * 
     * @param text
     *            : text with or without line modifications
     * @param offset
     *            : the offset wanted
     * @return : Returns the same offset if textArea is not a JTextArea, returns
     *         the correct one if it is.
     * @throws BadLocationException
     *             : An exception is raised of the offset does not exist in the
     *             original text, which should never happen.
     */
    public int getTextAreaOffset(String text, int offset) throws BadLocationException {
	if (textArea instanceof JTextArea) {
	    JTextArea txtTmp = new JTextArea(text);
	    int line = txtTmp.getLineOfOffset(offset);
	    int offsetFromLine = offset - txtTmp.getLineStartOffset(line);
	    return ((JTextArea) textArea).getLineStartOffset(line) + offsetFromLine;
	} else
	    return offset;
    }

    /**
     * <i>Extracted from rhino for debugging puproses.</i><br/>
     * Always returns a human-readable string for the token name. For instance,
     * {@link #FINALLY} has the name "FINALLY".
     * 
     * @param token
     *            the token code
     * @return the actual name for the token code
     */
    public String typeToName(int token) {
	switch (token) {
	case Token.EOF:
	    return "EOF";
	case Token.EOL:
	    return "EOL";
	case Token.ENTERWITH:
	    return "ENTERWITH";
	case Token.LEAVEWITH:
	    return "LEAVEWITH";
	case Token.RETURN:
	    return "RETURN";
	case Token.GOTO:
	    return "GOTO";
	case Token.IFEQ:
	    return "IFEQ";
	case Token.IFNE:
	    return "IFNE";
	case Token.SETNAME:
	    return "SETNAME";
	case Token.BITOR:
	    return "BITOR";
	case Token.BITXOR:
	    return "BITXOR";
	case Token.BITAND:
	    return "BITAND";
	case Token.EQ:
	    return "EQ";
	case Token.NE:
	    return "NE";
	case Token.LT:
	    return "LT";
	case Token.LE:
	    return "LE";
	case Token.GT:
	    return "GT";
	case Token.GE:
	    return "GE";
	case Token.LSH:
	    return "LSH";
	case Token.RSH:
	    return "RSH";
	case Token.URSH:
	    return "URSH";
	case Token.ADD:
	    return "ADD";
	case Token.SUB:
	    return "SUB";
	case Token.MUL:
	    return "MUL";
	case Token.DIV:
	    return "DIV";
	case Token.MOD:
	    return "MOD";
	case Token.NOT:
	    return "NOT";
	case Token.BITNOT:
	    return "BITNOT";
	case Token.POS:
	    return "POS";
	case Token.NEG:
	    return "NEG";
	case Token.NEW:
	    return "NEW";
	case Token.DELPROP:
	    return "DELPROP";
	case Token.TYPEOF:
	    return "TYPEOF";
	case Token.GETPROP:
	    return "GETPROP";
	case Token.SETPROP:
	    return "SETPROP";
	case Token.GETELEM:
	    return "GETELEM";
	case Token.SETELEM:
	    return "SETELEM";
	case Token.CALL:
	    return "CALL";
	case Token.NAME:
	    return "NAME";
	case Token.NUMBER:
	    return "NUMBER";
	case Token.STRING:
	    return "STRING";
	case Token.NULL:
	    return "NULL";
	case Token.THIS:
	    return "THIS";
	case Token.FALSE:
	    return "FALSE";
	case Token.TRUE:
	    return "TRUE";
	case Token.SHEQ:
	    return "SHEQ";
	case Token.SHNE:
	    return "SHNE";
	case Token.REGEXP:
	    return "REGEXP";
	case Token.BINDNAME:
	    return "BINDNAME";
	case Token.THROW:
	    return "THROW";
	case Token.RETHROW:
	    return "RETHROW";
	case Token.IN:
	    return "IN";
	case Token.INSTANCEOF:
	    return "INSTANCEOF";
	case Token.LOCAL_LOAD:
	    return "LOCAL_LOAD";
	case Token.GETVAR:
	    return "GETVAR";
	case Token.SETVAR:
	    return "SETVAR";
	case Token.CATCH_SCOPE:
	    return "CATCH_SCOPE";
	case Token.ENUM_INIT_KEYS:
	    return "ENUM_INIT_KEYS";
	case Token.ENUM_INIT_VALUES:
	    return "ENUM_INIT_VALUES";
	case Token.ENUM_NEXT:
	    return "ENUM_NEXT";
	case Token.ENUM_ID:
	    return "ENUM_ID";
	case Token.THISFN:
	    return "THISFN";
	case Token.RETURN_RESULT:
	    return "RETURN_RESULT";
	case Token.ARRAYLIT:
	    return "ARRAYLIT";
	case Token.OBJECTLIT:
	    return "OBJECTLIT";
	case Token.GET_REF:
	    return "GET_REF";
	case Token.SET_REF:
	    return "SET_REF";
	case Token.DEL_REF:
	    return "DEL_REF";
	case Token.REF_CALL:
	    return "REF_CALL";
	case Token.REF_SPECIAL:
	    return "REF_SPECIAL";
	case Token.DEFAULTNAMESPACE:
	    return "DEFAULTNAMESPACE";
	case Token.ESCXMLTEXT:
	    return "ESCXMLTEXT";
	case Token.ESCXMLATTR:
	    return "ESCXMLATTR";
	case Token.REF_MEMBER:
	    return "REF_MEMBER";
	case Token.REF_NS_MEMBER:
	    return "REF_NS_MEMBER";
	case Token.REF_NAME:
	    return "REF_NAME";
	case Token.REF_NS_NAME:
	    return "REF_NS_NAME";
	case Token.TRY:
	    return "TRY";
	case Token.SEMI:
	    return "SEMI";
	case Token.LB:
	    return "LB";
	case Token.RB:
	    return "RB";
	case Token.LC:
	    return "LC";
	case Token.RC:
	    return "RC";
	case Token.LP:
	    return "LP";
	case Token.RP:
	    return "RP";
	case Token.COMMA:
	    return "COMMA";
	case Token.ASSIGN:
	    return "ASSIGN";
	case Token.ASSIGN_BITOR:
	    return "ASSIGN_BITOR";
	case Token.ASSIGN_BITXOR:
	    return "ASSIGN_BITXOR";
	case Token.ASSIGN_BITAND:
	    return "ASSIGN_BITAND";
	case Token.ASSIGN_LSH:
	    return "ASSIGN_LSH";
	case Token.ASSIGN_RSH:
	    return "ASSIGN_RSH";
	case Token.ASSIGN_URSH:
	    return "ASSIGN_URSH";
	case Token.ASSIGN_ADD:
	    return "ASSIGN_ADD";
	case Token.ASSIGN_SUB:
	    return "ASSIGN_SUB";
	case Token.ASSIGN_MUL:
	    return "ASSIGN_MUL";
	case Token.ASSIGN_DIV:
	    return "ASSIGN_DIV";
	case Token.ASSIGN_MOD:
	    return "ASSIGN_MOD";
	case Token.HOOK:
	    return "HOOK";
	case Token.COLON:
	    return "COLON";
	case Token.OR:
	    return "OR";
	case Token.AND:
	    return "AND";
	case Token.INC:
	    return "INC";
	case Token.DEC:
	    return "DEC";
	case Token.DOT:
	    return "DOT";
	case Token.FUNCTION:
	    return "FUNCTION";
	case Token.EXPORT:
	    return "EXPORT";
	case Token.IMPORT:
	    return "IMPORT";
	case Token.IF:
	    return "IF";
	case Token.ELSE:
	    return "ELSE";
	case Token.SWITCH:
	    return "SWITCH";
	case Token.CASE:
	    return "CASE";
	case Token.DEFAULT:
	    return "DEFAULT";
	case Token.WHILE:
	    return "WHILE";
	case Token.DO:
	    return "DO";
	case Token.FOR:
	    return "FOR";
	case Token.BREAK:
	    return "BREAK";
	case Token.CONTINUE:
	    return "CONTINUE";
	case Token.VAR:
	    return "VAR";
	case Token.WITH:
	    return "WITH";
	case Token.CATCH:
	    return "CATCH";
	case Token.FINALLY:
	    return "FINALLY";
	case Token.VOID:
	    return "VOID";
	case Token.RESERVED:
	    return "RESERVED";
	case Token.EMPTY:
	    return "EMPTY";
	case Token.BLOCK:
	    return "BLOCK";
	case Token.LABEL:
	    return "LABEL";
	case Token.TARGET:
	    return "TARGET";
	case Token.LOOP:
	    return "LOOP";
	case Token.EXPR_VOID:
	    return "EXPR_VOID";
	case Token.EXPR_RESULT:
	    return "EXPR_RESULT";
	case Token.JSR:
	    return "JSR";
	case Token.SCRIPT:
	    return "SCRIPT";
	case Token.TYPEOFNAME:
	    return "TYPEOFNAME";
	case Token.USE_STACK:
	    return "USE_STACK";
	case Token.SETPROP_OP:
	    return "SETPROP_OP";
	case Token.SETELEM_OP:
	    return "SETELEM_OP";
	case Token.LOCAL_BLOCK:
	    return "LOCAL_BLOCK";
	case Token.SET_REF_OP:
	    return "SET_REF_OP";
	case Token.DOTDOT:
	    return "DOTDOT";
	case Token.COLONCOLON:
	    return "COLONCOLON";
	case Token.XML:
	    return "XML";
	case Token.DOTQUERY:
	    return "DOTQUERY";
	case Token.XMLATTR:
	    return "XMLATTR";
	case Token.XMLEND:
	    return "XMLEND";
	case Token.TO_OBJECT:
	    return "TO_OBJECT";
	case Token.TO_DOUBLE:
	    return "TO_DOUBLE";
	}

	// Token without name
	throw new IllegalStateException(String.valueOf(token));
    }

    /**
     * <i>Extracted from rhino for debugging puproses.</i> Convert a keyword
     * token to a name string for use with the
     * {@link Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER} feature.
     * 
     * @param token
     *            A token
     * @return the corresponding name string
     */
    public static String keywordToName(int token) {
	switch (token) {
	case Token.BREAK:
	    return "break";
	case Token.CASE:
	    return "case";
	case Token.CONTINUE:
	    return "continue";
	case Token.DEFAULT:
	    return "default";
	case Token.DELPROP:
	    return "delete";
	case Token.DO:
	    return "do";
	case Token.ELSE:
	    return "else";
	case Token.FALSE:
	    return "false";
	case Token.FOR:
	    return "for";
	case Token.FUNCTION:
	    return "function";
	case Token.IF:
	    return "if";
	case Token.IN:
	    return "in";
	case Token.NEW:
	    return "new";
	case Token.NULL:
	    return "null";
	case Token.RETURN:
	    return "return";
	case Token.SWITCH:
	    return "switch";
	case Token.THIS:
	    return "this";
	case Token.TRUE:
	    return "true";
	case Token.TYPEOF:
	    return "typeof";
	case Token.VAR:
	    return "var";
	case Token.VOID:
	    return "void";
	case Token.WHILE:
	    return "while";
	case Token.WITH:
	    return "with";
	case Token.CATCH:
	    return "catch";
	case Token.FINALLY:
	    return "finally";
	case Token.INSTANCEOF:
	    return "instanceof";
	case Token.THROW:
	    return "throw";
	case Token.TRY:
	    return "try";
	default:
	    return null;
	}
    }
}
