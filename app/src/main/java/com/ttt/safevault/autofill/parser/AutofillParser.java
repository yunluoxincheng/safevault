package com.ttt.safevault.autofill.parser;

import android.app.assist.AssistStructure;
import android.os.Build;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.SaveRequest;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;

import com.ttt.safevault.autofill.model.AutofillField;
import com.ttt.safevault.autofill.model.AutofillParsedData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 自动填充解析器
 * 负责从FillRequest和SaveRequest中解析出可填充的字段信息
 */
public class AutofillParser {
    private static final String TAG = "AutofillParser";
    
    // 用户名字段的hint关键词
    private static final List<String> USERNAME_HINTS = Arrays.asList(
            View.AUTOFILL_HINT_USERNAME,
            View.AUTOFILL_HINT_EMAIL_ADDRESS,
            "username", "user", "login", "account", "email", "e-mail",
            "userid", "user_id", "user-id", "user_name", "user-name",
            "loginid", "login_id", "login-id", "accountid", "account_id",
            "identifier", "uname"
    );
    
    // 密码字段的hint关键词
    private static final List<String> PASSWORD_HINTS = Arrays.asList(
            View.AUTOFILL_HINT_PASSWORD,
            "password", "pass", "pwd"
    );
    
    // 邮箱字段的hint关键词
    private static final List<String> EMAIL_HINTS = Arrays.asList(
            View.AUTOFILL_HINT_EMAIL_ADDRESS,
            "email", "e-mail", "mail", "emailaddress", "email_address", "email-address",
            "邮箱", "电子邮件", "电子邮箱"
    );
    
    // 手机号字段的hint关键词
    private static final List<String> PHONE_HINTS = Arrays.asList(
            View.AUTOFILL_HINT_PHONE,
            "phone", "mobile", "tel", "telephone", "cellphone", "cell",
            "phonenumber", "phone_number", "phone-number",
            "mobilenumber", "mobile_number", "mobile-number",
            "手机", "手机号", "电话", "联系电话", "移动电话"
    );
    
    // 身份证字段的hint关键词
    private static final List<String> ID_CARD_HINTS = Arrays.asList(
            "idcard", "id_card", "id-card", "identitycard", "identity_card",
            "idnumber", "id_number", "id-number", "cardno", "card_no", "card-no",
            "idno", "id_no", "id-no", "certificateno", "certificate_no",
            "身份证", "身份证号", "证件号", "证件号码"
    );
    
    // 需要排除的字段（验证码、搜索框等）
    private static final List<String> EXCLUDE_HINTS = Arrays.asList(
            "captcha", "verify", "verification", "vcode", "checkcode", "authcode",
            "validatecode", "validcode", "imgcode", "piccode", "seccode",
            "验证码", "图形码", "图片码",
            "search", "query", "keyword", "find", "搜索", "查找",
            "otp", "sms", "smscode", "phonecode", "短信码", "手机码",
            "code", "pin"
    );

    /**
     * 解析FillRequest，提取字段信息
     *
     * @param request FillRequest对象
     * @return 解析后的数据
     */
    public static AutofillParsedData parseFillRequest(FillRequest request) {
        logDebug("=== 开始解析 FillRequest ===");
        
        if (request == null) {
            logDebug("FillRequest为null");
            return null;
        }

        List<FillContext> contexts = request.getFillContexts();
        if (contexts == null || contexts.isEmpty()) {
            logDebug("FillContexts为空");
            return null;
        }

        // 使用最新的FillContext
        FillContext fillContext = contexts.get(contexts.size() - 1);
        AssistStructure structure = fillContext.getStructure();
        return parseAssistStructure(structure);
    }

    /**
     * 解析SaveRequest，提取要保存的字段值
     *
     * @param request SaveRequest对象
     * @return 解析后的数据
     */
    public static AutofillParsedData parseSaveRequest(SaveRequest request) {
        logDebug("=== 开始解析 SaveRequest ===");
        
        if (request == null) {
            logDebug("SaveRequest为null");
            return null;
        }

        List<FillContext> contexts = request.getFillContexts();
        if (contexts == null || contexts.isEmpty()) {
            logDebug("FillContexts为空");
            return null;
        }

        // 使用最新的FillContext
        FillContext fillContext = contexts.get(contexts.size() - 1);
        AssistStructure structure = fillContext.getStructure();
        AutofillParsedData parsedData = parseAssistStructure(structure);
        
        // 提取实际字段值
        if (parsedData != null) {
            extractFieldValues(structure, parsedData);
        }
        
        return parsedData;
    }

    /**
     * 从 AssistStructure 中提取字段的实际值
     */
    private static void extractFieldValues(AssistStructure structure, AutofillParsedData parsedData) {
        logDebug("=== 开始提取字段实际值 ===");
        
        int windowCount = structure.getWindowNodeCount();
        for (int i = 0; i < windowCount; i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode rootNode = windowNode.getRootViewNode();
            
            if (rootNode != null) {
                extractFieldValuesFromNode(rootNode, parsedData);
            }
        }
    }

    /**
     * 递归提取 ViewNode 中的字段值
     */
    private static void extractFieldValuesFromNode(AssistStructure.ViewNode node, 
                                                   AutofillParsedData parsedData) {
        // 检查是否有 AutofillValue
        AutofillId autofillId = node.getAutofillId();
        AutofillValue autofillValue = node.getAutofillValue();
        
        if (autofillId != null && autofillValue != null) {
            String value = extractValueFromAutofillValue(autofillValue, node);
            
            if (value != null && !value.isEmpty()) {
                // 在 parsedData 的字段列表中找到对应的字段并更新值
                updateFieldValue(parsedData, autofillId, value);
                
                // 日志输出（密码不记录明文）
                AutofillField.FieldType fieldType = getFieldTypeById(parsedData, autofillId);
                if (fieldType == AutofillField.FieldType.PASSWORD) {
                    logDebug("提取到密码字段值（长度: " + value.length() + "）");
                } else {
                    logDebug("提取到字段值: " + value + " (type: " + fieldType + ")");
                }
            }
        }
        
        // 递归处理子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AssistStructure.ViewNode childNode = node.getChildAt(i);
            if (childNode != null) {
                extractFieldValuesFromNode(childNode, parsedData);
            }
        }
    }

    /**
     * 从 AutofillValue 提取字符串值
     */
    private static String extractValueFromAutofillValue(AutofillValue autofillValue, 
                                                        AssistStructure.ViewNode node) {
        if (autofillValue == null) {
            return null;
        }
        
        // 尝试从 AutofillValue 获取文本值
        if (autofillValue.isText()) {
            CharSequence text = autofillValue.getTextValue();
            if (text != null) {
                return text.toString();
            }
        }
        
        // 回退到 getText()
        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.length() > 0) {
            return nodeText.toString();
        }
        
        return null;
    }

    /**
     * 更新 parsedData 中对应字段的值
     */
    private static void updateFieldValue(AutofillParsedData parsedData, 
                                        AutofillId targetId, String value) {
        List<AutofillField> fields = parsedData.getFields();
        
        // 找到对应的字段并替换为带有 value 的新字段
        for (int i = 0; i < fields.size(); i++) {
            AutofillField field = fields.get(i);
            if (field.getAutofillId().equals(targetId)) {
                // 创建新的 AutofillField，包含实际值
                AutofillField newField = new AutofillField(
                    field.getAutofillId(),
                    field.getHint(),
                    value,
                    field.getInputType(),
                    field.isFocused(),
                    field.getFieldType()
                );
                fields.set(i, newField);
                break;
            }
        }
    }

    /**
     * 根据 AutofillId 获取字段类型
     */
    private static AutofillField.FieldType getFieldTypeById(AutofillParsedData parsedData, 
                                                            AutofillId autofillId) {
        for (AutofillField field : parsedData.getFields()) {
            if (field.getAutofillId().equals(autofillId)) {
                return field.getFieldType();
            }
        }
        return AutofillField.FieldType.UNKNOWN;
    }

    /**
     * 解析AssistStructure
     */
    private static AutofillParsedData parseAssistStructure(AssistStructure structure) {
        AutofillParsedData.Builder builder = new AutofillParsedData.Builder();

        int windowCount = structure.getWindowNodeCount();
        logDebug("Window数量: " + windowCount);

        for (int i = 0; i < windowCount; i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode rootNode = windowNode.getRootViewNode();
            
            if (rootNode != null) {
                parseViewNode(rootNode, builder);
            }
        }

        return builder.build();
    }

    /**
     * 递归解析ViewNode
     */
    private static void parseViewNode(AssistStructure.ViewNode node, 
                                      AutofillParsedData.Builder builder) {
        // 提取元数据
        extractMetadata(node, builder);

        // 检查是否为可填充字段
        if (isAutofillable(node)) {
            AutofillField field = createAutofillField(node);
            if (field != null) {
                builder.addField(field);
                logDebug("找到可填充字段: " + field);
            }
        }

        // 递归处理子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AssistStructure.ViewNode childNode = node.getChildAt(i);
            if (childNode != null) {
                parseViewNode(childNode, builder);
            }
        }
    }

    /**
     * 提取元数据（域名、包名、应用名称、标题）
     */
    private static void extractMetadata(AssistStructure.ViewNode node, 
                                       AutofillParsedData.Builder builder) {
        // 提取Web域名
        String webDomain = node.getWebDomain();
        if (webDomain != null && !webDomain.isEmpty()) {
            builder.setDomain(webDomain);
            builder.setIsWeb(true);
            logDebug("检测到Web域名: " + webDomain);
        }

        // 提取包名
        String idPackage = node.getIdPackage();
        if (idPackage != null && !idPackage.isEmpty()) {
            builder.setPackageName(idPackage);
            logDebug("检测到包名: " + idPackage);
        }
        
        // 提取页面标题（尝试从多个来源获取）
        extractTitle(node, builder);
    }

    /**
     * 提取页面标题
     */
    private static void extractTitle(AssistStructure.ViewNode node, 
                                    AutofillParsedData.Builder builder) {
        // 如果已经有标题，直接返回
        // if (builder.title != null && !builder.title.isEmpty()) {
        //     return;
        // }
        
        // 尝试从 ViewNode 的 text 属性获取（可能是页面标题）
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String textStr = text.toString().trim();
            // 检查是否可能是标题（不太短也不太长）
            if (textStr.length() > 3 && textStr.length() < 100) {
                // 避免将常见的表单标签当作标题
                String lowerText = textStr.toLowerCase(Locale.ROOT);
                boolean isFormLabel = false;
                for (String hint : USERNAME_HINTS) {
                    if (lowerText.contains(hint)) {
                        isFormLabel = true;
                        break;
                    }
                }
                for (String hint : PASSWORD_HINTS) {
                    if (lowerText.contains(hint)) {
                        isFormLabel = true;
                        break;
                    }
                }
                
                if (!isFormLabel) {
                    logDebug("检测到可能的标题: " + textStr);
                    // 不直接设置，留在后端处理
                }
            }
        }
        
        // Web 页面：尝试从 HTML 信息中获取 title
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
            if (htmlInfo != null) {
                String htmlTag = htmlInfo.getTag();
                if ("title".equalsIgnoreCase(htmlTag)) {
                    CharSequence titleText = node.getText();
                    if (titleText != null && titleText.length() > 0) {
                        String title = titleText.toString().trim();
                        builder.setTitle(title);
                        logDebug("从 HTML <title> 提取到标题: " + title);
                    }
                }
            }
        }
    }

    /**
     * 判断节点是否可自动填充
     */
    private static boolean isAutofillable(AssistStructure.ViewNode node) {
        // 检查AutofillId
        AutofillId autofillId = node.getAutofillId();
        if (autofillId == null) {
            return false;
        }

        // 检查AutofillType
        int autofillType = node.getAutofillType();
        // 支持TEXT和NONE类型（有些Web字段可能是NONE）
        if (autofillType != View.AUTOFILL_TYPE_TEXT && 
            autofillType != View.AUTOFILL_TYPE_NONE) {
            return false;
        }

        // 检查是否有hint、inputType或是Web表单
        String[] hints = node.getAutofillHints();
        int inputType = node.getInputType();
        String webDomain = node.getWebDomain();
        boolean isWebField = (webDomain != null && !webDomain.isEmpty());
        
        // Web字段更宽松的判断
        if (isWebField) {
            // Web字段可能没有hints和inputType
            return true;
        }
        
        return (hints != null && hints.length > 0) || inputType != 0;
    }

    /**
     * 创建AutofillField对象
     */
    private static AutofillField createAutofillField(AssistStructure.ViewNode node) {
        AutofillId autofillId = node.getAutofillId();
        int inputType = node.getInputType();
        boolean isFocused = node.isFocused();

        // 识别字段类型
        AutofillField.FieldType fieldType = identifyFieldType(node);
        
        // 获取hint文本
        String hint = getHintText(node);
        
        // 详细日志
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("字段详情: ");
        logMsg.append("idEntry=").append(node.getIdEntry());
        logMsg.append(", hint=").append(hint);
        logMsg.append(", inputType=").append(inputType);
        logMsg.append(", autofillType=").append(node.getAutofillType());
        logMsg.append(", fieldType=").append(fieldType);
        logMsg.append(", isFocused=").append(isFocused);
        logMsg.append(", webDomain=").append(node.getWebDomain());
        logMsg.append(", className=").append(node.getClassName());
        
        // 输出 autofillHints
        String[] hints = node.getAutofillHints();
        if (hints != null && hints.length > 0) {
            logMsg.append(", autofillHints=[");
            for (int i = 0; i < hints.length; i++) {
                if (i > 0) logMsg.append(", ");
                logMsg.append(hints[i]);
            }
            logMsg.append("]");
        }
        
        logDebug(logMsg.toString());

        return new AutofillField(autofillId, hint, inputType, isFocused, fieldType);
    }

    /**
     * 识别字段类型
     */
    private static AutofillField.FieldType identifyFieldType(AssistStructure.ViewNode node) {
        // 0. 首先检查是否为需要排除的字段（验证码、搜索框等）
        if (isExcludedField(node)) {
            logDebug("字段被排除（验证码/搜索框等）");
            return AutofillField.FieldType.UNKNOWN;
        }
        
        // 1. 优先根据autofillHints识别
        String[] hints = node.getAutofillHints();
        if (hints != null && hints.length > 0) {
            for (String hint : hints) {
                if (hint == null) continue;
                
                String lowerHint = hint.toLowerCase(Locale.ROOT);
                
                // 检查密码hint（密码优先级最高）
                for (String passwordHint : PASSWORD_HINTS) {
                    if (lowerHint.contains(passwordHint)) {
                        return AutofillField.FieldType.PASSWORD;
                    }
                }
                
                // 检查手机号hint
                for (String phoneHint : PHONE_HINTS) {
                    if (lowerHint.contains(phoneHint)) {
                        return AutofillField.FieldType.PHONE;
                    }
                }
                
                // 检查身份证hint
                for (String idCardHint : ID_CARD_HINTS) {
                    if (lowerHint.contains(idCardHint)) {
                        return AutofillField.FieldType.ID_CARD;
                    }
                }
                
                // 检查邮箱hint
                for (String emailHint : EMAIL_HINTS) {
                    if (lowerHint.contains(emailHint)) {
                        return AutofillField.FieldType.EMAIL;
                    }
                }
                
                // 检查用户名hint
                for (String usernameHint : USERNAME_HINTS) {
                    if (lowerHint.contains(usernameHint)) {
                        return AutofillField.FieldType.USERNAME;
                    }
                }
            }
        }

        // 2. 根据inputType识别
        int inputType = node.getInputType();
        int typeClass = inputType & InputType.TYPE_MASK_CLASS;
        int typeVariation = inputType & InputType.TYPE_MASK_VARIATION;

        // 密码类型
        if (typeVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            typeVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            typeVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
            return AutofillField.FieldType.PASSWORD;
        }

        // 邮箱类型
        if (typeVariation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            typeVariation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            return AutofillField.FieldType.EMAIL;
        }
        
        // 手机号类型
        if (typeClass == InputType.TYPE_CLASS_PHONE) {
            return AutofillField.FieldType.PHONE;
        }

        // 3. 根据hint文本识别（作为备用）
        CharSequence hintText = node.getHint();
        if (hintText != null) {
            String lowerHintText = hintText.toString().toLowerCase(Locale.ROOT);
            
            // 检查密码
            for (String passwordHint : PASSWORD_HINTS) {
                if (lowerHintText.contains(passwordHint)) {
                    return AutofillField.FieldType.PASSWORD;
                }
            }
            
            // 检查手机号
            for (String phoneHint : PHONE_HINTS) {
                if (lowerHintText.contains(phoneHint)) {
                    return AutofillField.FieldType.PHONE;
                }
            }
            
            // 检查身份证
            for (String idCardHint : ID_CARD_HINTS) {
                if (lowerHintText.contains(idCardHint)) {
                    return AutofillField.FieldType.ID_CARD;
                }
            }
            
            // 检查邮箱
            for (String emailHint : EMAIL_HINTS) {
                if (lowerHintText.contains(emailHint)) {
                    return AutofillField.FieldType.EMAIL;
                }
            }
            
            // 检查用户名
            for (String usernameHint : USERNAME_HINTS) {
                if (lowerHintText.contains(usernameHint)) {
                    return AutofillField.FieldType.USERNAME;
                }
            }
        }

        // 4. 根据id名称识别（作为最后的备用）
        String idEntry = node.getIdEntry();
        if (idEntry != null) {
            String lowerIdEntry = idEntry.toLowerCase(Locale.ROOT);
            
            // 检查密码
            for (String passwordHint : PASSWORD_HINTS) {
                if (lowerIdEntry.contains(passwordHint)) {
                    return AutofillField.FieldType.PASSWORD;
                }
            }
            
            // 检查手机号
            for (String phoneHint : PHONE_HINTS) {
                if (lowerIdEntry.contains(phoneHint)) {
                    return AutofillField.FieldType.PHONE;
                }
            }
            
            // 检查身份证
            for (String idCardHint : ID_CARD_HINTS) {
                if (lowerIdEntry.contains(idCardHint)) {
                    return AutofillField.FieldType.ID_CARD;
                }
            }
            
            // 检查邮箱
            for (String emailHint : EMAIL_HINTS) {
                if (lowerIdEntry.contains(emailHint)) {
                    return AutofillField.FieldType.EMAIL;
                }
            }
            
            // 检查用户名
            for (String usernameHint : USERNAME_HINTS) {
                if (lowerIdEntry.contains(usernameHint)) {
                    return AutofillField.FieldType.USERNAME;
                }
            }
        }

        // 5. 根据htmlInfo识别（仅Web）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
            if (htmlInfo != null) {
                String htmlTag = htmlInfo.getTag();
                
                // 获取所有HTML属性
                String htmlType = null;
                String htmlName = null;
                String htmlId = null;
                String htmlAutocomplete = null;
                String htmlPlaceholder = null;
                
                for (int i = 0; i < htmlInfo.getAttributes().size(); i++) {
                    android.util.Pair<String, String> attr = htmlInfo.getAttributes().get(i);
                    String attrName = attr.first;
                    String attrValue = attr.second;
                    
                    if ("type".equals(attrName)) {
                        htmlType = attrValue;
                    } else if ("name".equals(attrName)) {
                        htmlName = attrValue;
                    } else if ("id".equals(attrName)) {
                        htmlId = attrValue;
                    } else if ("autocomplete".equals(attrName)) {
                        htmlAutocomplete = attrValue;
                    } else if ("placeholder".equals(attrName)) {
                        htmlPlaceholder = attrValue;
                    }
                }
                
                logDebug("HTML属性: tag=" + htmlTag + ", type=" + htmlType + 
                        ", name=" + htmlName + ", id=" + htmlId + 
                        ", autocomplete=" + htmlAutocomplete + ", placeholder=" + htmlPlaceholder);
                
                if ("input".equalsIgnoreCase(htmlTag)) {
                    // 根据type属性识别
                    if ("password".equalsIgnoreCase(htmlType)) {
                        return AutofillField.FieldType.PASSWORD;
                    } else if ("email".equalsIgnoreCase(htmlType)) {
                        return AutofillField.FieldType.EMAIL;
                    } else if ("tel".equalsIgnoreCase(htmlType)) {
                        return AutofillField.FieldType.PHONE;
                    }
                    
                    // 根据autocomplete属性识别
                    if (htmlAutocomplete != null) {
                        String lowerAutocomplete = htmlAutocomplete.toLowerCase(Locale.ROOT);
                        if (lowerAutocomplete.contains("password")) {
                            return AutofillField.FieldType.PASSWORD;
                        }
                        if (lowerAutocomplete.contains("tel") || 
                            lowerAutocomplete.contains("phone") ||
                            lowerAutocomplete.contains("mobile")) {
                            return AutofillField.FieldType.PHONE;
                        }
                        if (lowerAutocomplete.contains("email")) {
                            return AutofillField.FieldType.EMAIL;
                        }
                        if (lowerAutocomplete.contains("username")) {
                            return AutofillField.FieldType.USERNAME;
                        }
                    }
                    
                    // 根据name属性识别
                    if (htmlName != null) {
                        String lowerName = htmlName.toLowerCase(Locale.ROOT);
                        // 检查密码
                        for (String passwordHint : PASSWORD_HINTS) {
                            if (lowerName.contains(passwordHint)) {
                                return AutofillField.FieldType.PASSWORD;
                            }
                        }
                        // 检查手机号
                        for (String phoneHint : PHONE_HINTS) {
                            if (lowerName.contains(phoneHint)) {
                                return AutofillField.FieldType.PHONE;
                            }
                        }
                        // 检查身份证
                        for (String idCardHint : ID_CARD_HINTS) {
                            if (lowerName.contains(idCardHint)) {
                                return AutofillField.FieldType.ID_CARD;
                            }
                        }
                        // 检查邮箱
                        for (String emailHint : EMAIL_HINTS) {
                            if (lowerName.contains(emailHint)) {
                                return AutofillField.FieldType.EMAIL;
                            }
                        }
                        // 检查用户名
                        for (String usernameHint : USERNAME_HINTS) {
                            if (lowerName.contains(usernameHint)) {
                                return AutofillField.FieldType.USERNAME;
                            }
                        }
                    }
                    
                    // 根据id属性识别
                    if (htmlId != null) {
                        String lowerId = htmlId.toLowerCase(Locale.ROOT);
                        // 检查密码
                        for (String passwordHint : PASSWORD_HINTS) {
                            if (lowerId.contains(passwordHint)) {
                                return AutofillField.FieldType.PASSWORD;
                            }
                        }
                        // 检查手机号
                        for (String phoneHint : PHONE_HINTS) {
                            if (lowerId.contains(phoneHint)) {
                                return AutofillField.FieldType.PHONE;
                            }
                        }
                        // 检查身份证
                        for (String idCardHint : ID_CARD_HINTS) {
                            if (lowerId.contains(idCardHint)) {
                                return AutofillField.FieldType.ID_CARD;
                            }
                        }
                        // 检查邮箱
                        for (String emailHint : EMAIL_HINTS) {
                            if (lowerId.contains(emailHint)) {
                                return AutofillField.FieldType.EMAIL;
                            }
                        }
                        // 检查用户名
                        for (String usernameHint : USERNAME_HINTS) {
                            if (lowerId.contains(usernameHint)) {
                                return AutofillField.FieldType.USERNAME;
                            }
                        }
                    }
                    
                    // 根据placeholder属性识别
                    if (htmlPlaceholder != null) {
                        String lowerPlaceholder = htmlPlaceholder.toLowerCase(Locale.ROOT);
                        // 检查密码
                        for (String passwordHint : PASSWORD_HINTS) {
                            if (lowerPlaceholder.contains(passwordHint)) {
                                return AutofillField.FieldType.PASSWORD;
                            }
                        }
                        // 检查手机号
                        for (String phoneHint : PHONE_HINTS) {
                            if (lowerPlaceholder.contains(phoneHint)) {
                                return AutofillField.FieldType.PHONE;
                            }
                        }
                        // 检查身份证
                        for (String idCardHint : ID_CARD_HINTS) {
                            if (lowerPlaceholder.contains(idCardHint)) {
                                return AutofillField.FieldType.ID_CARD;
                            }
                        }
                        // 检查邮箱
                        for (String emailHint : EMAIL_HINTS) {
                            if (lowerPlaceholder.contains(emailHint)) {
                                return AutofillField.FieldType.EMAIL;
                            }
                        }
                        // 检查用户名
                        for (String usernameHint : USERNAME_HINTS) {
                            if (lowerPlaceholder.contains(usernameHint)) {
                                return AutofillField.FieldType.USERNAME;
                            }
                        }
                    }
                    
                    // 不再默认把所有text类型都当作用户名
                    // 只有明确匹配到用户名关键词的才认为是用户名字段
                }
            }
        }

        return AutofillField.FieldType.UNKNOWN;
    }

    /**
     * 检查是否为需要排除的字段（验证码、搜索框等）
     */
    private static boolean isExcludedField(AssistStructure.ViewNode node) {
        // 检查autofillHints
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String hint : hints) {
                if (hint != null && isExcludedText(hint)) {
                    return true;
                }
            }
        }
        
        // 检查hint文本
        CharSequence hintText = node.getHint();
        if (hintText != null && isExcludedText(hintText.toString())) {
            return true;
        }
        
        // 检查idEntry
        String idEntry = node.getIdEntry();
        if (idEntry != null && isExcludedText(idEntry)) {
            return true;
        }
        
        // 检查HTML属性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
            if (htmlInfo != null) {
                for (int i = 0; i < htmlInfo.getAttributes().size(); i++) {
                    android.util.Pair<String, String> attr = htmlInfo.getAttributes().get(i);
                    String attrName = attr.first;
                    String attrValue = attr.second;
                    
                    // 检查name、id、placeholder等属性
                    if (("name".equals(attrName) || "id".equals(attrName) || 
                         "placeholder".equals(attrName) || "autocomplete".equals(attrName)) &&
                        attrValue != null && isExcludedText(attrValue)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查文本是否包含排除关键词
     */
    private static boolean isExcludedText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String excludeHint : EXCLUDE_HINTS) {
            if (lowerText.contains(excludeHint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取hint文本
     */
    private static String getHintText(AssistStructure.ViewNode node) {
        // 优先使用autofillHints
        String[] hints = node.getAutofillHints();
        if (hints != null && hints.length > 0 && hints[0] != null) {
            return hints[0];
        }

        // 使用hint字段
        CharSequence hint = node.getHint();
        if (hint != null) {
            return hint.toString();
        }

        // 使用text字段（可能是label）
        CharSequence text = node.getText();
        if (text != null) {
            return text.toString();
        }

        return "";
    }

    /**
     * 调试日志输出到文件
     */
    private static void logDebug(String message) {
        Log.d(TAG, message);

        // 同时输出到文件（用于手机端调试）
        // 使用静态变量缓存FileWriter，减少频繁创建销毁的开销
        FileWriter writer = null;
        try {
            String logDir = "/storage/emulated/0/Android/data/com.ttt.safevault/files/autofill_logs/";
            File dir = new File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String logMessage = timestamp + " [" + TAG + "] " + message + "\n";

            File logFile = new File(dir, "autofill_parser.log");
            writer = new FileWriter(logFile, true);
            writer.write(logMessage);
        } catch (IOException e) {
            // 日志写入失败，使用系统日志记录
            android.util.Log.e(TAG, "Failed to write to log file", e);
        } finally {
            // 确保FileWriter正确关闭，防止资源泄漏
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // 关闭失败时记录日志
                    android.util.Log.e(TAG, "Failed to close log file writer", e);
                }
            }
        }
    }
}
