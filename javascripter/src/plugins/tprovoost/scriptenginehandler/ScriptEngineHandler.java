package plugins.tprovoost.scriptenginehandler;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.colormap.FireColorMap;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLoader;
import icy.plugin.abstract_.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.fife.ui.autocomplete.ParameterizedCompletion.Parameter;
import org.xeustechnologies.jcl.JarClassLoader;

import plugins.tprovoost.scripteditor.completion.IcyCompletionProvider;
import plugins.tprovoost.scriptenginehandler.ScriptFunctionCompletion.BindingFunction;

public class ScriptEngineHandler {

    /*---------------
     *	   static
     *---------------*/
    /**
     * This {@link HashMap} is used to avoid multiple different engines for the
     * same language to be initialized.
     */
    private static final HashMap<String, ScriptEngine> engines = new HashMap<String, ScriptEngine>();

    /** The factory contains all the engines. */
    public static final ScriptEngineManager factory = new ScriptEngineManager(PluginLoader.getLoader());
    private static HashMap<ScriptEngine, ScriptEngineHandler> engineHandlers = new HashMap<ScriptEngine, ScriptEngineHandler>();
    private static ScriptEngineHandler lastEngineHandler = null;
    private static ArrayList<Method> bindingFunctions;

    /*
     * ------------ non static ------------
     */
    private HashMap<String, Class<?>> engineVariables = new HashMap<String, Class<?>>();
    private HashMap<String, Class<?>> engineFunctions = new HashMap<String, Class<?>>();
    private ArrayList<String> engineDeclaredImports = new ArrayList<String>();
    private ArrayList<String> engineDeclaredImportClasses = new ArrayList<String>();
    private HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> engineTypesMethod = new HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>>();;

    private ScriptEngineHandler() {
	if (bindingFunctions == null) {
	    bindingFunctions = new ArrayList<Method>();
	    ProgressFrame frame = new ProgressFrame("Loading functions...");
	    // Chronometer chrono = new Chronometer("chrono");
	    try {
		if (getClass().getClassLoader() instanceof JarClassLoader) {
		    Collection<Class<?>> col = PluginLoader.getAllClasses().values();
		    frame.setLength(col.size());
		    int i = 0;
		    for (Class<?> clazz : new ArrayList<Class<?>>(col)) {
			if (clazz.getName().startsWith("plugins.tprovoost.scripteditor"))
			    continue;
			findBindingsMethods(clazz);
			++i;
			frame.setPosition(i);
		    }
		} else {
		    ArrayList<PluginDescriptor> list = PluginLoader.getPlugins();
		    frame.setLength(list.size());
		    int i = 0;
		    for (PluginDescriptor pd : list) {
			// System.out.println(pd);
			findBindingsMethods(pd.getPluginClass());
			++i;
			frame.setPosition(i);
		    }
		}
	    } finally {
		// chrono.displayInSeconds();
		frame.close();
	    }
	}
    }
    
    public static ScriptEngineHandler getLastEngineHandler() {
	return lastEngineHandler;
    }

    public static void setEngine(String engineType, ScriptEngine engine) {
	engines.put(engineType, engine);
    }

    public static ScriptEngineHandler getEngineHandler(ScriptEngine engine) {
	ScriptEngineHandler engineHandler = engineHandlers.get(engine);
	if (engineHandler == null) {
	    engineHandler = new ScriptEngineHandler();
	    engineHandlers.put(engine, engineHandler);
	}
	lastEngineHandler = engineHandler;
	return engineHandler;
    }

    public static ScriptEngine getEngine(String engineType) {
	return getEngine(engineType, false);
    }

    public static ScriptEngine getEngine(String engineType, boolean create) {
	ScriptEngine engineHash = engines.get(engineType);
	if (engineHash == null || create) {
	    engineHash = factory.getEngineByName(engineType);
	    engines.put(engineType, engineHash);
	}
	return engineHash;
    }

    // public ScriptEngine generateNewEngine() {
    // String engineType = engine.getFactory().getLanguageName();
    // ScriptEngine engine = factory.getEngineByName(engineType);
    // engines.put(engineType, engine);
    // this.engine = engine;
    // return engine;
    // }

    public ArrayList<String> getEngineDeclaredImportClasses() {
	return engineDeclaredImportClasses;
    }

    public ArrayList<String> getEngineDeclaredImports() {
	return engineDeclaredImports;
    }

    public HashMap<String, Class<?>> getEngineFunctions() {
	return engineFunctions;
    }

    public HashMap<String, Class<?>> getEngineVariables() {
	return engineVariables;
    }

    public HashMap<Class<?>, ArrayList<ScriptFunctionCompletion>> getEngineTypesMethod() {
	return engineTypesMethod;
    }

    public static ScriptEngineManager getFactory() {
	return factory;
    }

    public void findBindingsMethods(Class<?> clazz) {
	if (clazz == null)
	    return;
	// get the annotated methods
	Method[] methods;
	try {
	    methods = clazz.getDeclaredMethods();
	} catch (Error e) {
	    return;
	}

	for (final Method method : methods) {

	    // make sure the method is public and annotated
	    int modifiers = method.getModifiers();
	    if (!Modifier.isPublic(modifiers))
		continue;

	    // is it an annotated with BindingFunction?
	    BindingFunction blockFunction = method.getAnnotation(BindingFunction.class);
	    if (blockFunction == null)
		continue;
	    bindingFunctions.add(method);

	    // Generate the function for the provider
	    ArrayList<Parameter> fParams = new ArrayList<Parameter>();
	    Class<?>[] paramTypes = method.getParameterTypes();

	    // get the parameters
	    String params = "";
	    String functionName = blockFunction.value();
	    // get the parameters
	    for (int i = 0; i < paramTypes.length; ++i) {
		fParams.add(new Parameter(IcyCompletionProvider.getType(paramTypes[i], true), "arg" + i));
		params += ",arg" + i;
	    }
	    if (params.length() > 0)
		params = params.substring(1);

	    // the object for the provider
	    ScriptFunctionCompletion sfc;
	    if (Modifier.isStatic(method.getModifiers()))
		sfc = new ScriptFunctionCompletion(null, functionName, method);
	    else
		sfc = new ScriptFunctionCompletion(null, method.getName(), method);
	    sfc.setDefinedIn(clazz.getName());
	    sfc.setParams(fParams);
	    sfc.setRelevance(2);

	    if (engineFunctions != null)
		engineFunctions.put(functionName, method.getReturnType());
	    if (engineTypesMethod != null) {
		ArrayList<ScriptFunctionCompletion> methodsExisting = engineTypesMethod.get(clazz);
		if (methodsExisting == null)
		    methodsExisting = new ArrayList<ScriptFunctionCompletion>();
		if (methodsExisting.contains(sfc))
		    methodsExisting.remove(sfc);
		methodsExisting.add(sfc);
		engineTypesMethod.put(clazz, methodsExisting);
	    }
	}
    }

    public ArrayList<Method> getFunctions() {
	return bindingFunctions;
    }

    // TODO
    private void fireEngineCHanged() {

    }

}
