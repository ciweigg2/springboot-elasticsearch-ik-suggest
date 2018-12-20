package com.zhou.essearch.controller;

import com.zhou.essearch.document.ProductDocument;
import com.zhou.essearch.page.Page;
import com.zhou.essearch.service.EsSearchService;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeAction;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.SuggestionBuilder;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * elasticsearch 搜索
 * @author zhoudong
 * @version 0.1
 * @date 2018/12/13 15:09
 */
@RestController
public class EsSearchController {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    private EsSearchService esSearchService;

    /**
     * 新增 / 修改索引
     * @return
     */
    @RequestMapping("save")
    public String add(@RequestBody ProductDocument productDocument) {
        esSearchService.save(productDocument);
        return "success";
    }

    /**
     * 删除索引
     * @return
     */
    @RequestMapping("delete/{id}")
    public String delete(@PathVariable String id) {
        esSearchService.delete(id);
        return "success";
    }
    /**
     * 清空索引
     * @return
     */
    @RequestMapping("delete_all")
    public String deleteAll() {
        esSearchService.deleteAll();
        return "success";
    }

    /**
     * 根据ID获取
     * @return
     */
    @RequestMapping("get/{id}")
    public ProductDocument getById(@PathVariable String id){
        return esSearchService.getById(id);
    }

    /**
     * 根据获取全部
     * @return
     */
    @RequestMapping("get_all")
    public List<ProductDocument> getAll(){
        return esSearchService.getAll();
    }

    /**
     * 搜索
     * @param keyword
     * @return
     */
    @RequestMapping("query/{keyword}")
    public List<ProductDocument> query(@PathVariable String keyword){
        return esSearchService.query(keyword,ProductDocument.class);
    }

    /**
     * 搜索，命中关键字高亮
     * http://localhost:8080/query_hit?keyword=无印良品荣耀&indexName=orders&fields=productName,productDesc
     * @param keyword   关键字
     * @param indexName 索引库名称
     * @param fields    搜索字段名称，多个以“，”分割
     * @return
     */
    @RequestMapping("query_hit")
    public List<Map<String,Object>> queryHit(@RequestParam String keyword, @RequestParam String indexName, @RequestParam String fields){
        String[] fieldNames = {};
        if(fields.contains(",")) fieldNames = fields.split(",");
        else fieldNames[0] = fields;
        return esSearchService.queryHit(keyword,indexName,fieldNames);
    }

    /**
     * 搜索，命中关键字高亮
     * http://localhost:8080/query_hit_page?keyword=无印良品荣耀&indexName=orders&fields=productName,productDesc&pageNo=1&pageSize=1
     * @param pageNo    当前页
     * @param pageSize  每页显示的数据条数
     * @param keyword   关键字
     * @param indexName 索引库名称
     * @param fields    搜索字段名称，多个以“，”分割
     * @return
     */
    @RequestMapping("query_hit_page")
    public Page<Map<String,Object>> queryHitByPage(@RequestParam int pageNo,@RequestParam int pageSize
                                                    ,@RequestParam String keyword, @RequestParam String indexName, @RequestParam String fields){
        String[] fieldNames = {};
        if(fields.contains(",")) fieldNames = fields.split(",");
        else fieldNames[0] = fields;
        return esSearchService.queryHitByPage(pageNo,pageSize,keyword,indexName,fieldNames);
    }

    /**
     * 删除索引库
     * @param indexName
     * @return
     */
    @RequestMapping("delete_index/{indexName}")
    public String deleteIndex(@PathVariable String indexName){
        esSearchService.deleteIndex(indexName);
        return "success";
    }

    @Autowired
    private Client client;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 建议搜索1
     * 先将keyword词语分词 然后用分词后的数据去匹配所有前缀为当前分词的数据
     *
     * @param keyword
     * @return
     */
    @RequestMapping("suggestIk")
    public List<String> getSuggestSearchIk(@RequestParam String keyword) {
        List responseSuggests = new ArrayList();
        //分词
        List<String> list = getAnalyzes("orders" ,keyword);
        list.forEach(kewords->{
            //查询建议词
            List<String> suggestKeyword = suggest(kewords);
            responseSuggests.addAll(suggestKeyword);
        });
        return responseSuggests;
    }

    /**
     * 建议搜索2
     * 根据前缀关联搜索所有匹配前缀的词语(搜索国民 可能会关联出国民小妹妹 国民美少女)
     *
     * @param keyword
     * @return
     */
    @RequestMapping("suggest")
    public List<String> getSuggestSearch(@RequestParam String keyword) {
        List<String> suggestKeyword = suggest(keyword);
        return suggestKeyword;
    }

    /**
     * 查看分词数据
     *
     * @param ikKeyword 分词数据
     * @return
     */
    @RequestMapping("ik")
    public List<String> getSuggestSearch(@RequestParam String index ,@RequestParam String ikKeyword) {
        return getAnalyzes(index ,ikKeyword);
    }

    /**
     * 将文本分词后的数据 默认使用中文ik_max_word分词
     *
     * @param index 索引index
     * @param text 需要被分析的词语
     * @return
     */
    public List<String> getAnalyzes(String index,String text){
        //调用ES客户端分词器进行分词
        AnalyzeRequestBuilder ikRequest = new AnalyzeRequestBuilder(elasticsearchTemplate.getClient(),
                AnalyzeAction.INSTANCE,index,text).setAnalyzer("ik_max_word");
        List<AnalyzeResponse.AnalyzeToken> ikTokenList = ikRequest.execute().actionGet().getTokens();

        // 赋值
        List<String> searchTermList = new ArrayList<>();
        ikTokenList.forEach(ikToken -> {
            searchTermList.add(ikToken.getTerm());
        });

        return searchTermList;
    }

    /**
     * 根据前缀关联搜索所有匹配前缀的词语(搜索国民 可能会关联出国民小妹妹 国民美少女)
     *
     * @param keyword
     * @return
     */
    public List<String> suggest(String keyword){
        //field的名字,前缀(输入的text),以及大小size
        CompletionSuggestionBuilder suggestionBuilderDistrict = SuggestBuilders.completionSuggestion("productName.suggest")
                .prefix(keyword).size(100);
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        suggestBuilder.addSuggestion("my-suggest", suggestionBuilderDistrict);//添加suggest my-suggest自定义的随便取的
        //设置查询builder的index,type,以及建议
        SearchRequestBuilder requestBuilder = client.prepareSearch("orders").setTypes("product").suggest(suggestBuilder);
        System.out.println(requestBuilder.toString());

        SearchResponse response = requestBuilder.get();
        Suggest suggest = response.getSuggest();//suggest实体

        Set<String> suggestSet = new HashSet<>();//set
        int maxSuggest = 0;
        if (suggest!=null){
            Suggest.Suggestion result = suggest.getSuggestion("my-suggest");//获取suggest,name任意string
            for (Object term : result.getEntries()) {

                if (term instanceof CompletionSuggestion.Entry){
                    CompletionSuggestion.Entry item = (CompletionSuggestion.Entry) term;
                    if (!item.getOptions().isEmpty()){
                        //若item的option不为空,循环遍历
                        for (CompletionSuggestion.Entry.Option option : item.getOptions()) {
                            String tip = option.getText().toString();
                            if (!suggestSet.contains(tip)){
                                suggestSet.add(tip);
                                ++maxSuggest;
                            }
                        }
                    }
                }
                if (maxSuggest>=5){
                    break;
                }
            }
        }
        List<String> suggests = Arrays.asList(suggestSet.toArray(new String[]{}));
        return suggests;
    }

}
