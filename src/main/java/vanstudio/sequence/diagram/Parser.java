package vanstudio.sequence.diagram;

import com.google.gson.Gson;
import com.google.gson.stream.MalformedJsonException;
import com.intellij.openapi.diagnostic.Logger;
import vanstudio.sequence.openapi.Constants;
import vanstudio.sequence.openapi.model.ClassDescription;
import vanstudio.sequence.openapi.model.LambdaExprDescription;
import vanstudio.sequence.openapi.model.MethodDescription;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Parser {

    private static final Logger LOGGER = Logger.getInstance(Parser.class);

    /**
     * 存储类中所调用的方法
     */
    private final CallInfoStack _callInfoStack = new CallInfoStack();

    /**
     * 包含 调用链 & 返回链，即实箭头 & 虚箭头
     */
    private final List<Link> _linkList = new ArrayList<>();

    /**
     * 最顶层的对象信息, 如 Actor Main ArrayList
     */
    private final List<ObjectInfo> _objInfoList = new ArrayList<>();

    /**
     * 当前最顶层的对象的水平顺序
     */
    private int _currentClassHorizontalSeq = 0;

    /**
     * 当前方法调用的垂直顺序
     */
    private int _curMethodCallVerticalSeq = 0;

    public Parser() {
    }

    public void parse(String sequenceStr) throws IOException {
        parse(new BufferedReader(new StringReader(sequenceStr)));
    }

    public void parse(BufferedReader reader) throws IOException {
        paseCalls(reader);
        resolveBackCalls();
    }

    private void paseCalls(BufferedReader reader) throws IOException {
        String line = null;
        while ((line = reader.readLine()) != null) {
            switch (line) {
                case "(":
                    break;
                case ")":
                    addReturn();
                    break;
                default: // 遇到描述方法的 json字符串
                    try {
                        addCall(line);
                    } catch (Throwable e) {
                        if (e instanceof MalformedJsonException) {
                            LOGGER.error("org.intellij.sequencer.diagram.Parser: " + line);
                        }
                        throw e;
                    }
                    break;
            }
        }
    }


    private void resolveBackCalls() {
        HashMap<Numbering, MethodInfo> callsMap = new HashMap<>();
        for (Link link : _linkList) {
            if (!(link instanceof Call))
                continue;
            callsMap.put(link.getMethodInfo().getNumbering(), link.getMethodInfo());
        }
        for (Link link : _linkList) {
            Numbering numbering = link.getMethodInfo().getNumbering().getPreviousNumbering();
            if (numbering != null)
                link.setCallerMethodInfo(callsMap.get(numbering));
        }
    }

    public List<Link> getLinks() {
        return _linkList;
    }

    public List<ObjectInfo> getObjects() {
        return _objInfoList;
    }

    /**
     * 整理归类调用信息
     * @param calledMethod JSON字符串
     */
    private void addCall(String calledMethod) {
        Gson gson = new Gson();
        MethodDescription methodDescription = gson.fromJson(calledMethod, MethodDescription.class);
        boolean isLambda = Objects.equals(methodDescription.getMethodName(), Constants.Lambda_Invoke);

        if (isLambda) {
            methodDescription = gson.fromJson(calledMethod, LambdaExprDescription.class);
        }
        // methodDescription: "|public|static|@main[args=java.Lang.String[]]:void"     classDescription: "|public|@Main"
        ClassDescription classDescription = methodDescription.getClassDescription();

        // 列表为空，连Actor都没有 存入Actor
        if (_objInfoList.isEmpty()) {
            ObjectInfo objectInfo = new ObjectInfo(ObjectInfo.ACTOR_NAME, new ArrayList<>(), _currentClassHorizontalSeq);// Actor是水平方向上第0个Object
            ++_currentClassHorizontalSeq;
            _objInfoList.add(objectInfo);
            CallInfo callInfo = new CallInfo(objectInfo, "ActorMethod", _curMethodCallVerticalSeq); // Actor类中的方法是垂直方向上第0个方法
            _callInfoStack.push(callInfo);
        }
        
        // 非 Actor类 添加到 _objInfoList
        ObjectInfo objectInfo = new ObjectInfo(classDescription.getClassName(), classDescription.getAttributes(), _currentClassHorizontalSeq);
        int i = _objInfoList.indexOf(objectInfo);
        // objInfoList没有该objInfo，添加进去，比如 ArrayList 出现多次，没必要创建多个重复的 ArrayList
        if (i == -1) {
            ++_currentClassHorizontalSeq;
            _objInfoList.add(objectInfo);
        } else {
            objectInfo = _objInfoList.get(i);
        }

        // 被调用者
        CallInfo calledInfo = isLambda ? new LambdaInfo(objectInfo, methodDescription, _curMethodCallVerticalSeq)
                : new CallInfo(objectInfo, methodDescription, _curMethodCallVerticalSeq);

        MethodInfo methodInfo = createMethodInfo(isLambda, calledInfo);

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("addCall(...) calling " + calledInfo + " seq is " + _curMethodCallVerticalSeq);

        if (!_callInfoStack.isEmpty()) { // lastCallInfo currentCallInfo 变量名更新
            // 弹栈，弹出调用者
            CallInfo callerInfo = _callInfoStack.peek();
            calledInfo.setNumbering(); // 设置调用方法的 x.x: ?

            // 创建从调用者到被调用者的调用链，如 Actor -> Main、 Main -> ArrayList
            Call call = callerInfo.createCall(calledInfo);
            call.setMethodInfo(methodInfo);
            call.setVerticalSeq(_curMethodCallVerticalSeq++); // VerticalSeq表示垂直方向上的调用顺序，调用算一次，返回算一次
            _linkList.add(call);
        }

        _callInfoStack.push(calledInfo);
    }

    @NotNull
    private MethodInfo createMethodInfo(boolean isLambda, CallInfo callInfo) {
        return isLambda ?
                new LambdaExprInfo(
                        callInfo.getObj(),
                        callInfo.getNumbering(), callInfo.getAttributes(),
                        callInfo.getMethod(), callInfo.getReturnType(),
                        callInfo.getArgNames(), callInfo.getArgTypes(),
                        callInfo.getStartingVerticalSeq(), _curMethodCallVerticalSeq,
                        ((LambdaInfo) callInfo).getEnclosedMethodName(),
                        ((LambdaInfo) callInfo).getEnclosedMethodArgTypes()
                ) :
                new MethodInfo(callInfo.getObj(),
                        callInfo.getNumbering(), callInfo.getAttributes(),
                        callInfo.getMethod(), callInfo.getReturnType(),
                        callInfo.getArgNames(), callInfo.getArgTypes(),
                        callInfo.getStartingVerticalSeq(), _curMethodCallVerticalSeq);
    }

    /**
     * 方法调用返回后，将该方法弹出，get方法还是由 main方法调用
     */
    private void addReturn() {
        CallInfo callInfo = _callInfoStack.pop();

        boolean isLambda = callInfo instanceof LambdaInfo;

        MethodInfo methodInfo = createMethodInfo(isLambda, callInfo);

        callInfo.getObj().addMethod(methodInfo);

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("addReturn(...) returning from " + callInfo + " seq is " + _curMethodCallVerticalSeq);

        if (!_callInfoStack.isEmpty()) {
            CallInfo currentInfo = _callInfoStack.peek();
            currentInfo.getCall().setMethodInfo(methodInfo);
            CallReturn call = new CallReturn(callInfo.getObj(), currentInfo.getObj());
            call.setMethodInfo(methodInfo);
            _linkList.add(call);
            call.setVerticalSeq(_curMethodCallVerticalSeq++);
        }
    }


    /**
     * Peek a sdt tile read top method of Sequence Diagram.
     *
     * @param f a .sdt file
     * @return MethodDescription
     */
    public static MethodDescription peek(File f) {
        try {
            List<String> query = Files.readAllLines(f.toPath());
            for (String s : query) {
                switch (s) {
                    case "(":
                    case ")":
                        continue;
                    default:
                        Gson gson = new Gson();
                        return gson.fromJson(s, MethodDescription.class);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 从上到下将各个方法调用入栈
     */
    private class CallInfoStack {
        private Stack<CallInfo> stack = new Stack<>();
        private CallInfo nPointerCallInfo;
        private int nPointerCounter;

        public void push(CallInfo callInfo) {
            stack.push(callInfo);
            nPointerCounter++;
            if (nPointerCounter > 1)
                nPointerCallInfo = callInfo;
        }

        public CallInfo pop() {
            CallInfo result = stack.pop();
            nPointerCallInfo = result;
            return result;
        }

        public Numbering getNumbering() {
            if (nPointerCallInfo == null)
                return peek().getNumbering();
            return nPointerCallInfo.getNumbering();
        }

        public CallInfo peek() {
            return stack.peek();
        }

        public int size() {
            return stack.size();
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }
    }
    /**
     * 类中的某个方法的调用信息 不包含 from、to
     */
    private class CallInfo {
        private final ObjectInfo _obj;
        private final String _method;
        private final List<String> _argNames = new ArrayList<>();
        private final List<String> _argTypes = new ArrayList<>();
        private final List<String> _attributes = new ArrayList<>();
        private String _returnType;

        private Numbering _numbering;
        private Call _call;
        private final int _startingSeq;

        CallInfo(ObjectInfo obj, String method, int startingSeq) {
            _obj = obj;
            _method = method;
            _startingSeq = startingSeq;
        }

        CallInfo(ObjectInfo obj, MethodDescription m, int startingSeq) {
            _obj = obj;
            _method = m.getMethodName();
            _attributes.addAll(m.getAttributes());
            _argNames.addAll(m.getArgNames());
            _argTypes.addAll(m.getArgTypes());
            _returnType = m.getReturnType();
            _startingSeq = startingSeq;
        }

        void setNumbering() {
            int stackLevel = _callInfoStack.size() - 1;
            Numbering numbering = _callInfoStack.getNumbering();
            _numbering = new Numbering(numbering);
            if (_numbering.level() <= stackLevel)
                _numbering.addNewLevel();
            else
                _numbering.incrementLevel(stackLevel);
        }

        Call createCall(CallInfo to) {
            _call = new Call(_obj, to.getObj());
            return _call;
        }

        Call getCall() {
            return _call;
        }

        ObjectInfo getObj() {
            return _obj;
        }

        public List<String> getAttributes() {
            return _attributes;
        }

        String getMethod() {
            return _method;
        }

        public String getReturnType() {
            return _returnType;
        }

        public List<String> getArgNames() {
            return _argNames;
        }

        public List<String> getArgTypes() {
            return _argTypes;
        }

        public Numbering getNumbering() {
            return _numbering;
        }

        int getStartingVerticalSeq() {
            return _startingSeq;
        }
        @Override
        public String toString() {
            return "Calling " + _method + " on " + _obj;
        }
    }

    private class LambdaInfo extends CallInfo {
        private final String _enclosedMethodName;
        private final List<String> _enclosedMethodArgTypes;

        public LambdaInfo(ObjectInfo obj, MethodDescription m, int startingSeq) {
            super(obj, m, startingSeq);
            LambdaExprDescription lm = (LambdaExprDescription) m;
            this._enclosedMethodName = lm.getEnclosedMethodName();
            this._enclosedMethodArgTypes = lm.getEnclosedMethodArgTypes();
        }

        public String getEnclosedMethodName() {
            return _enclosedMethodName;
        }

        public List<String> getEnclosedMethodArgTypes() {
            return _enclosedMethodArgTypes;
        }
    }
}
