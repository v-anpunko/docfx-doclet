package com.microsoft.lookup;

import static com.microsoft.util.ElementUtil.convertFullNameToOverload;

import com.microsoft.lookup.model.ExtendedMetadataFileItem;
import com.microsoft.model.ExceptionItem;
import com.microsoft.model.MethodParameter;
import com.microsoft.model.Return;
import com.microsoft.util.ElementUtil;
import com.sun.source.doctree.DocTree.Kind;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.apache.commons.lang3.StringUtils;

public class ClassItemsLookup extends BaseLookup<Element> {

    @Override
    protected ExtendedMetadataFileItem buildMetadataFileItem(Element element) {
        String packageName = ElementUtil.extractPackageName(element);
        TypeElement classElement = (TypeElement) element.getEnclosingElement();
        String classQName = String.valueOf(classElement.getQualifiedName());
        String elementQName = String.valueOf(element);
        String classQNameWithGenericsSupport = String.valueOf(classElement.asType());
        String classSNameWithGenericsSupport = classQNameWithGenericsSupport.replace(packageName.concat("."), "");
        String uid = String.format("%s.%s", classQName, elementQName);

        ExtendedMetadataFileItem result = new ExtendedMetadataFileItem() {{
            setUid(uid);
            setId(elementQName);
            setParent(classQName);
            setHref(classQName + ".yml");
            setName(elementQName);
            setType(ElementUtil.extractType(element));
            setPackageName(packageName);
            setSummary(ElementUtil.extractComment(element));
        }};

        String modifiers = element.getModifiers().stream().map(String::valueOf).collect(Collectors.joining(" "));
        if (element instanceof ExecutableElement) {
            result.setConstructorContent(String.format("%s %s", modifiers, elementQName));

            ExecutableElement exeElement = (ExecutableElement) element;
            List<MethodParameter> parameters = extractParameters(exeElement);
            String paramsString = parameters.stream()
                .map(parameter -> String.format("%s %s", parameter.getType(), parameter.getId()))
                .collect(Collectors.joining(", "));
            String nameWithoutBrackets = elementQName.replaceAll("\\(.*\\)", "");
            String methodName = String.format("%s(%s)", nameWithoutBrackets, paramsString);

            result.setName(methodName);
            result.setMethodContent(String.format("%s %s %s", modifiers, exeElement.getReturnType(), result.getName()));
            result.setParameters(parameters);
            result.setExceptions(extractExceptions(exeElement));
            result.setReturn(extractReturn(exeElement));
        }
        result.setNameWithType(String.format("%s.%s", classSNameWithGenericsSupport, result.getName()));
        result.setFullName(String.format("%s.%s", classQNameWithGenericsSupport, result.getName()));
        result.setOverload(convertFullNameToOverload(result.getFullName()));

        if (element instanceof VariableElement) {
            result.setFieldContent(String.format("%s %s", modifiers, elementQName));
            result.setReturn(extractReturn((VariableElement) element));
        }
        return result;
    }

    List<MethodParameter> extractParameters(ExecutableElement element) {
        return element.getParameters().stream().map(o -> {
            String paramName = String.valueOf(o.getSimpleName());
            String paramType = String.valueOf(o.asType());
            return new MethodParameter(paramName, paramType, extractParameterDescription(element, paramName));
        }).collect(Collectors.toList());
    }

    String extractParameterDescription(ExecutableElement method, String paramName) {
        return ElementUtil.getDocCommentTree(method).map(docTree -> docTree.getBlockTags().stream()
            .filter(o -> o.getKind() == Kind.PARAM)
            .map(String::valueOf)
            .map(o -> StringUtils.remove(o, "@param"))
            .map(StringUtils::trim)
            .filter(o -> o.startsWith(paramName))
            .map(o -> StringUtils.replace(o, paramName, ""))
            .map(StringUtils::trim)
            .findFirst().orElse(null)
        ).orElse(null);
    }

    List<ExceptionItem> extractExceptions(ExecutableElement methodElement) {
        return methodElement.getThrownTypes().stream().map(o -> {
            String exceptionType = String.valueOf(o);
            return new ExceptionItem(exceptionType, extractExceptionDescription(methodElement, exceptionType));
        }).collect(Collectors.toList());
    }

    String extractExceptionDescription(ExecutableElement methodElement, String exceptionType) {
        return ElementUtil.getDocCommentTree(methodElement).map(docTree -> docTree.getBlockTags().stream()
            .filter(o -> o.getKind() == Kind.THROWS)
            .map(String::valueOf)
            .map(o -> StringUtils.remove(o, "@throws"))
            .map(StringUtils::trim)
            .filter(o -> o.contains(" "))
            .filter(o -> StringUtils.contains(exceptionType, o.substring(0, o.indexOf(" "))))
            .map(o -> StringUtils.replace(o, o.substring(0, o.indexOf(" ")), ""))
            .map(StringUtils::trim)
            .findFirst().orElse(null)
        ).orElse(null);
    }

    Return extractReturn(ExecutableElement methodElement) {
        return new Return(String.valueOf(methodElement.getReturnType()),
            extractReturnDescription(methodElement));
    }

    String extractReturnDescription(ExecutableElement methodElement) {
        return ElementUtil.getDocCommentTree(methodElement).map(docTree -> docTree.getBlockTags().stream()
            .filter(o -> o.getKind() == Kind.RETURN)
            .map(String::valueOf)
            .map(o -> StringUtils.remove(o, "@return"))
            .map(StringUtils::trim)
            .findFirst().orElse(null)
        ).orElse(null);
    }

    Return extractReturn(VariableElement fieldElement) {
        return new Return(String.valueOf(fieldElement.asType()));
    }
}