package com.example.achuan.uploadimagetest_0.config;

/**
 * Created by achuan on 16-9-15.
 */
public class LabConfig {
    public final static String LOG_TAG="QiniuLab";
    //访问链接的后缀　　　(固定不变的)
    public final static String CONSTANT_SUFFIXES_SERVER = ".bkt.clouddn.com";
    //头像空间的前缀　　　(不同的空间前缀不同,后缀相同)
    public final static String UNCONSTANT_PREFIXES_SERVER="odizyh9ki";



    public static String makeUrl(String unconstant_prefixes, String constant_suffixes) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        sb.append(unconstant_prefixes);//前缀
        sb.append(constant_suffixes);//后缀
        return sb.toString();//完整的访问链接
    }


}
