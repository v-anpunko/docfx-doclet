package com.microsoft.build;

import static com.microsoft.util.ElementUtil.convertFullNameToOverload;
import static com.microsoft.util.ElementUtil.determineClassSimpleName;
import static com.microsoft.util.ElementUtil.extractClassContent;
import static com.microsoft.util.ElementUtil.extractExceptions;
import static com.microsoft.util.ElementUtil.extractPackageContent;
import static com.microsoft.util.ElementUtil.extractPackageElements;
import static com.microsoft.util.ElementUtil.extractParameters;
import static com.microsoft.util.ElementUtil.extractReturn;
import static com.microsoft.util.ElementUtil.extractSortedElements;
import static com.microsoft.util.ElementUtil.extractSuperclass;
import static com.microsoft.util.ElementUtil.extractType;
import static com.microsoft.util.ElementUtil.extractTypeParameters;

import com.microsoft.model.MetadataFile;
import com.microsoft.model.MetadataFileItem;
import com.microsoft.model.TocFile;
import com.microsoft.model.TocItem;
import com.microsoft.util.FileUtil;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import jdk.javadoc.doclet.DocletEnvironment;

public class YmlFilesBuilder {

    private final static String[] LANGS = {"java"};

    private DocletEnvironment environment;
    private String outputPath;

    public YmlFilesBuilder(DocletEnvironment environment, String outputPath) {
        this.environment = environment;
        this.outputPath = outputPath;
    }

    public boolean build() {
        TocFile tocFile = new TocFile(outputPath);
        for (PackageElement packageElement : extractPackageElements(environment.getIncludedElements())) {
            String packageQName = String.valueOf(packageElement.getQualifiedName());
            String packageYmlFileName = packageQName + ".yml";
            buildPackageYmlFile(packageElement, packageYmlFileName);

            TocItem packageTocItem = new TocItem(packageQName, packageQName, packageYmlFileName);
            buildFilesForInnerClasses("", packageElement, packageTocItem.getItems());
            tocFile.addTocItem(packageTocItem);
        }
        FileUtil.dumpToFile(tocFile);
        return true;
    }

    void buildFilesForInnerClasses(String namePrefix, Element element, List<TocItem> listToAddItems) {
        for (TypeElement classElement : extractSortedElements(element)) {
            String classQName = String.valueOf(classElement.getQualifiedName());
            String classSimpleName = determineClassSimpleName(namePrefix, classElement);
            String classYmlFileName = classQName + ".yml";
            buildClassYmlFile(classElement, classYmlFileName);

            TocItem classTocItem = new TocItem(classQName, classSimpleName, classYmlFileName);
            listToAddItems.add(classTocItem);

            buildFilesForInnerClasses(classSimpleName, classElement, listToAddItems);
        }
    }

    void buildPackageYmlFile(PackageElement packageElement, String fileName) {
        MetadataFile metadataFile = new MetadataFile(outputPath, fileName);
        String qName = String.valueOf(packageElement.getQualifiedName());
        String sName = String.valueOf(packageElement.getSimpleName());

        MetadataFileItem packageItem = new MetadataFileItem(LANGS);
        packageItem.setUid(qName);
        packageItem.setId(sName);
        addPackageChildren(qName, "", packageElement, packageItem.getChildren(), metadataFile.getReferences());
        packageItem.setHref(qName + ".yml");
        packageItem.setName(qName);
        packageItem.setNameWithType(qName);
        packageItem.setFullName(qName);
        packageItem.setType(extractType(packageElement));
        packageItem.setSummary(extractComment(packageElement));
        packageItem.setContent(extractPackageContent(packageElement));
        metadataFile.getItems().add(packageItem);
        FileUtil.dumpToFile(metadataFile);
    }

    void addPackageChildren(String packageName, String namePrefix, Element packageElement, List<String> packageChildren,
        List<MetadataFileItem> references) {
        for (TypeElement classElement : extractSortedElements(packageElement)) {
            String qName = String.valueOf(classElement.getQualifiedName());
            String sName = determineClassSimpleName(namePrefix, classElement);

            MetadataFileItem reference = buildClassReference(classElement);
            references.add(reference);

            packageChildren.add(qName);
            addPackageChildren(packageName, sName, classElement, packageChildren, references);
        }
    }

    MetadataFileItem buildShortClassReference(TypeElement classElement) {
        String qName = String.valueOf(classElement.getQualifiedName());
        String qNameWithGenericsSupport = String.valueOf(classElement.asType());
        String packageName = String.valueOf(environment.getElementUtils().getPackageOf(classElement));
        String shortNameWithGenericsSupport = qNameWithGenericsSupport.replace(packageName + ".", "");

        MetadataFileItem referenceItem = new MetadataFileItem();
        referenceItem.setUid(qName);
        referenceItem.setParent(packageName);
        referenceItem.setHref(qName + ".yml");
        referenceItem.setName(shortNameWithGenericsSupport);
        referenceItem.setNameWithType(shortNameWithGenericsSupport);
        referenceItem.setFullName(qNameWithGenericsSupport);
        referenceItem.setType(extractType(classElement));
        referenceItem.setSummary(extractComment(classElement));
        return referenceItem;
    }

    MetadataFileItem buildClassReference(TypeElement classElement) {
        String qNameWithGenericsSupport = String.valueOf(classElement.asType());
        String packageName = String.valueOf(environment.getElementUtils().getPackageOf(classElement));
        String shortNameWithGenericsSupport = qNameWithGenericsSupport.replace(packageName + ".", "");

        MetadataFileItem referenceItem = buildShortClassReference(classElement);
        referenceItem.setContent(extractClassContent(classElement, shortNameWithGenericsSupport));
        referenceItem.setTypeParameters(extractTypeParameters(classElement));
        return referenceItem;
    }

    String extractComment(Element element) {
        return environment.getElementUtils().getDocComment(element);
    }

    void buildClassYmlFile(TypeElement classElement, String fileName) {
        MetadataFile classMetadataFile = new MetadataFile(outputPath, fileName);

        String packageName = String.valueOf(environment.getElementUtils().getPackageOf(classElement));
        String classQName = String.valueOf(classElement.getQualifiedName());
        String classSName = String.valueOf(classElement.getSimpleName());
        String classQNameWithGenericsSupport = String.valueOf(classElement.asType());
        String classSNameWithGenericsSupport = classQNameWithGenericsSupport.replace(packageName + ".", "");

        // Add class info
        MetadataFileItem classItem = new MetadataFileItem(LANGS);
        classItem.setUid(classQName);
        classItem.setId(classSName);
        classItem.setParent(packageName);
        for (ExecutableElement methodElement : ElementFilter.methodsIn(classElement.getEnclosedElements())) {
            classItem.getChildren().add(String.valueOf(methodElement));
        }
        classItem.setHref(classQName + ".yml");
        classItem.setName(classSNameWithGenericsSupport);
        classItem.setNameWithType(classSNameWithGenericsSupport);
        classItem.setFullName(classQNameWithGenericsSupport);
        classItem.setType(extractType(classElement));
        classItem.setPackageName(packageName);
        classItem.setSummary(extractComment(classElement));
        classItem.setContent(extractClassContent(classElement, classSNameWithGenericsSupport));
        classItem.setSuperclass(extractSuperclass(classElement));
        classItem.setTypeParameters(extractTypeParameters(classElement));
        classMetadataFile.getItems().add(classItem);

        // Add constructors info
        for (ExecutableElement constructorElement : ElementFilter.constructorsIn(classElement.getEnclosedElements())) {
            MetadataFileItem constructorItem = buildMetadataFileItem(classQName, classQNameWithGenericsSupport,
                constructorElement);
            String constructorQName = String.valueOf(constructorElement);
            String fullName = String.format("%s.%s", classQNameWithGenericsSupport, constructorQName);

            constructorItem.setOverload(convertFullNameToOverload(fullName));
            String constructorContentValue = String.format("%s %s",
                constructorElement.getModifiers().stream().map(String::valueOf).collect(Collectors.joining(" ")),
                constructorQName);
            constructorItem.setContent(constructorContentValue);
            constructorItem.setParameters(extractParameters(constructorElement));
            classMetadataFile.getItems().add(constructorItem);
        }

        // Add methods info
        for (ExecutableElement methodElement : ElementFilter.methodsIn(classElement.getEnclosedElements())) {
            MetadataFileItem methodItem = buildMetadataFileItem(classQName, classQNameWithGenericsSupport,
                methodElement);
            String methodQName = String.valueOf(methodElement);
            String fullName = String.format("%s.%s", classQNameWithGenericsSupport, methodQName);

            methodItem.setOverload(convertFullNameToOverload(fullName));
            String methodContentValue = String.format("%s %s %s",
                methodElement.getModifiers().stream().map(String::valueOf).collect(Collectors.joining(" ")),
                methodElement.getReturnType(), methodQName);
            methodItem.setContent(methodContentValue);
            methodItem.setExceptions(extractExceptions(methodElement));
            methodItem.setParameters(extractParameters(methodElement));
            methodItem.setReturn(extractReturn(methodElement));

            classMetadataFile.getItems().add(methodItem);
        }

        // Add fields info
        for (VariableElement fieldElement : ElementFilter.fieldsIn(classElement.getEnclosedElements())) {
            MetadataFileItem fieldItem = buildMetadataFileItem(classQName, classQNameWithGenericsSupport, fieldElement);
            String fieldQName = String.valueOf(fieldElement);

            String fieldContentValue = String.format("%s %s",
                fieldElement.getModifiers().stream().map(String::valueOf).collect(Collectors.joining(" ")),
                fieldQName);
            fieldItem.setContent(fieldContentValue);
            fieldItem.setReturn(extractReturn(fieldElement));
            classMetadataFile.getItems().add(fieldItem);
        }

        // Add references info
        // Owner class reference
        classMetadataFile.getReferences().add(buildShortClassReference(classElement));

        FileUtil.dumpToFile(classMetadataFile);
    }

    MetadataFileItem buildMetadataFileItem(String classQName, String classQNameWithGenericsSupport, Element element) {
        MetadataFileItem metadataFileItem = new MetadataFileItem(LANGS);
        String elementQName = String.valueOf(element);
        String fullName = String.format("%s.%s", classQNameWithGenericsSupport, elementQName);
        String packageName = String.valueOf(environment.getElementUtils().getPackageOf(element));
        String classSNameWithGenericsSupport = classQNameWithGenericsSupport.replace(packageName + ".", "");

        metadataFileItem.setUid(String.format("%s.%s", classQName, elementQName));
        metadataFileItem.setId(elementQName);
        metadataFileItem.setParent(classQName);
        metadataFileItem.setHref(classQName + ".yml");
        metadataFileItem.setName(elementQName);
        metadataFileItem.setNameWithType(String.format("%s.%s", classSNameWithGenericsSupport, elementQName));
        metadataFileItem.setFullName(fullName);
        metadataFileItem.setType(extractType(element));
        metadataFileItem.setPackageName(packageName);
        metadataFileItem.setSummary(extractComment(element));
        return metadataFileItem;
    }
}
