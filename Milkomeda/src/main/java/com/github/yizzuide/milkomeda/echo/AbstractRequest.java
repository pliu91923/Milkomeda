package com.github.yizzuide.milkomeda.echo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.yizzuide.milkomeda.universe.config.MilkomedaProperties;
import com.github.yizzuide.milkomeda.util.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * AbstractRequest
 * 抽象请求类
 *
 * @author yizzuide
 * @since 1.13.0
 * @version 1.13.4
 * Create at 2019/09/21 16:48
 */
@Slf4j
public abstract class AbstractRequest {

    @Resource(name = "echoRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private MilkomedaProperties milkomedaProperties;

    /**
     * 获取消息体数据
     * 注意：消息体输入流只能读取一次，通过Filter包装一个Request才可以用这个方法
     *
     * @param inputStream InputStream
     * @return String
     */
    public static String getPostData(InputStream inputStream) {
        StringBuilder data;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String str;
            data = new StringBuilder();
            while((str = reader.readLine()) != null) {
                data.append(str);
            }
        } catch (IOException e) {
            log.error("abstractRequest:- get request body error with message: {}", e.getMessage(), e);
            return null;
        }
        return data.toString();
    }

    /**
     * Post方式向第三方发送请求，返回封装的ResponseData（默认data为Map类型）
     *
     * @param url 请求子路径
     * @param map 请求参数
     * @return EchoResponseData
     * @throws EchoException 请求异常
     */
    public EchoResponseData<Map<String, Object>> sendPostForResult(String url, Map<String, Object> map) throws EchoException {
        return sendPostForResult(url, map, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Post方式向第三方发送请求，返回封装的ResponseData（需要指定data的具体类型）
     *
     * @param url         请求子路径
     * @param params      请求参数
     * @param specType    具体类型
     * @param <T>         EchoResponseData的data字段类型
     * @return EchoResponseData
     * @throws EchoException 请求异常
     */
    public <T> EchoResponseData<T> sendPostForResult(String url, Map<String, Object> params, TypeReference<T> specType) throws EchoException {
        return sendPostForResult(url, params, specType, false);
    }

    /**
     * Post方式向第三方发送请求，返回封装的ResponseData（需要指定data的具体类型）
     *
     * @param url         请求子路径
     * @param params      请求参数
     * @param specType    EchoResponseData的data字段类型
     * @param forceCamel  是否强制驼峰字段，支持深度转换
     * @param <T>         EchoResponseData的data字段类型
     * @return EchoResponseData
     * @throws EchoException 请求异常
     */
    public <T> EchoResponseData<T> sendPostForResult(String url, Map<String, Object> params, TypeReference<T> specType, boolean forceCamel) throws EchoException {
        return sendRequest(HttpMethod.POST, url, params, specType, forceCamel);
    }

    /**
     * GET方式向第三方发送请求，返回封装的ResponseData（默认data为Map类型）
     * @param url       请求URL
     * @param params    请求参数
     * @return  EchoResponseData
     * @throws EchoException 请求异常
     */
    public EchoResponseData<Map<String, Object>> sendGetForResult(String url, Map<String, Object> params) throws EchoException {
        return sendRequest(HttpMethod.GET, url, params, new TypeReference<Map<String, Object>>() {}, false);
    }

    /**
     * 发送REST请求，返回封装的ResponseData（默认data为Map类型）
     * @param method    请求方式
     * @param url       请求URL
     * @param params    请求参数
     * @return  EchoResponseData
     * @throws EchoException 请求异常
     */
    public EchoResponseData<Map<String, Object>> sendRequestForResult(HttpMethod method, String url, Map<String, Object> params) throws EchoException {
        return sendRequest(method, url, params, new TypeReference<Map<String, Object>>() {}, false);
    }

    /**
     * 发送REST请求
     * @param method        请求方式
     * @param url           请求URL
     * @param params        请求参数，GET 和 DELETE 方式将会拼接在URL后面
     * @param specType      EchoResponseData的data字段类型
     * @param forceCamel    是否强制驼峰字段，支持深度转换
     * @param <T>           EchoResponseData的data字段类型
     * @return  EchoResponseData
     * @throws EchoException 请求异常
     */
    @SuppressWarnings("unchecked")
    public <T> EchoResponseData<T> sendRequest(HttpMethod method, String url, Map<String, Object> params, TypeReference<T> specType, boolean forceCamel) throws EchoException {
        // 请求头
        HttpHeaders headers = new HttpHeaders();
        appendHeaders(headers);
        // 请求参数
        Map reqParams = new HashMap();
        // 有消息体的请求方式
        if (hasBody(method)) {
            // 表单类型，转LinkedMultiValueMap，支持value数组
            if (MediaType.APPLICATION_FORM_URLENCODED.equals(headers.getContentType()) ||
                    MediaType.MULTIPART_FORM_DATA.equals(headers.getContentType())) {
                reqParams = new LinkedMultiValueMap<String, Object>();
            }
        }

        // 追加签名等参数
        signParam(params, reqParams);
        boolean showLog = milkomedaProperties.isShowLog();
        if (showLog) {
            log.info("abstractRequest:- send request with url: {}, params: {}, reqParams:{}", url, params, reqParams);
        }
        HttpEntity<Map> httpEntity = new HttpEntity<>(hasBody(method) ? reqParams : null, headers);
        ResponseEntity<Map> request;
        if (hasBody(method)) {
            request = restTemplate.exchange(url, method, httpEntity, Map.class);
        } else {
            // 转换为URL参数
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            for (Object k : reqParams.keySet()) {
                builder.queryParam((String) k, reqParams.get(k));
            }
            request = reqParams.size() > 0 ? restTemplate.exchange(builder.build().encode().toUri(), method, httpEntity, Map.class) :
                    restTemplate.exchange(url, method, httpEntity, Map.class);
        }
        Map body = request.getBody();
        boolean useStandardHTTP = useStandardHTTP();
        if (null == body) {
            if (!useStandardHTTP) {
                log.error("abstractRequest:- response with url: {}, params: {}, reqParams:{}, data: null", url, params, reqParams);
                throw new EchoException(ErrorCode.VENDOR_RESPONSE_IS_NOTHING, "response body is null");
            }
        }
        if (showLog) {
            log.info("abstractRequest:- response with url: {}, params: {}, reqParams:{}, data: {}", url, params, reqParams, body);
        }
        // 下划线转驼峰
        if (forceCamel && null != body) {
            try {
                body = JSONUtil.toCamel(body, new TypeReference<Map>() {});
            } catch (Exception e) {
                log.error("abstractRequest:- convert type data  error: {}", e.getMessage(), e);
                throw new EchoException(ErrorCode.VENDOR_SERVER_RESPONSE_DATA_ANALYSIS_FAIL, e.getMessage());
            }
        }
        EchoResponseData<T> responseData = createReturnData(body, specType, useStandardHTTP);
        if (useStandardHTTP) {
            responseData.setCode(String.valueOf(request.getStatusCodeValue()));
        }
        checkResponse(responseData);
        return responseData;
    }

    private boolean hasBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT;
    }

    /**
     * 是否使用标准的HTTP标准码（消息体中只有业务数据，不包code、msg这些）
     * @return 默认为false
     */
    protected boolean useStandardHTTP() {
        return false;
    }

    /**
     * 返回数据类型的模板方法
     *
     * @param respData 第三方方响应的数据
     * @param specType ResponseData的data字段类型
     * @param <T>      EchoResponseData的data字段类型
     * @param useStandardHTTP 是否使用标准的HTTP标准码
     * @return 统一响应数据类
     * @throws EchoException 请求异常
     */
    protected abstract <T> EchoResponseData<T> createReturnData(Map respData, TypeReference<T> specType, boolean useStandardHTTP) throws EchoException;

    /**
     * 子类需要实现的参数签名（默认不应用签名）
     *
     * @param inParams  需要签名的业务参数
     * @param outParams 加上签名后的参数，如果Content-Type是APPLICATION_FORM_URLENCODED, 则类型为LinkedMultiValueMap，添加参数需要调用add方法
     */
    @SuppressWarnings("unchecked")
    protected void signParam(Map<String, Object> inParams, Map<String, Object> outParams) {
        if (outParams instanceof LinkedMultiValueMap) {
            LinkedMultiValueMap multiValueMap = (LinkedMultiValueMap) outParams;
            for (Map.Entry<String, Object> inEntry : inParams.entrySet()) {
                multiValueMap.add(inEntry.getKey(), inEntry.getValue());
            }
        } else {
            for (Map.Entry<String, Object> inEntry : inParams.entrySet()) {
                outParams.put(inEntry.getKey(), inEntry.getValue());
            }
        }
    }

    /**
     * 默认添加application/json，需要添加其它的头信息可以覆盖这个方法
     *
     * @param headers HttpHeaders
     */
    protected void appendHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        headers.add("Accept", MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 检测响应数据的正确性
     *
     * @param responseData 统一响应数据类
     * @throws EchoException 请求异常
     */
    protected void checkResponse(EchoResponseData responseData) throws EchoException {}

    /**
     * 对第三方平台的请求参数验签
     *
     * @param inParams  请求参数
     * @return  解签后的业务数据，解签失败返回null
     */
    public Map<String, Object> verifyParam(Map<String, Object> inParams) { return null; }
}
