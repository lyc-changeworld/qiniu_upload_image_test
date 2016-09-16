package com.example.achuan.uploadimagetest_0.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.achuan.uploadimagetest_0.R;
import com.example.achuan.uploadimagetest_0.config.LabConfig;
import com.example.achuan.uploadimagetest_0.utils.DomainUtils;
import com.example.achuan.uploadimagetest_0.utils.Tools;
import com.ipaulpro.afilechooser.utils.FileUtils;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.android.utils.AsyncRun;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private MainActivity content;
    private static final int REQUEST_CODE = 8090;
    private String uploadFilePath;//要上传的文件的路径
    private UploadManager uploadManager;

    private long uploadLastTimePoint;//历史的时间点
    private long uploadLastOffset;//历史已经上传的文件块的长度
    private long uploadFileLength;//文件的总长度

    @Bind(R.id.bt_select)
    Button mBtSelect;
    @Bind(R.id.bt_upload)
    Button mBtUpload;
    @Bind(R.id.quick_start_image_upload_speed_textview)
    TextView mQuickStartImageUploadSpeedTextview;
    @Bind(R.id.quick_start_image_upload_file_length_textview)
    TextView mQuickStartImageUploadFileLengthTextview;
    @Bind(R.id.quick_start_image_upload_percentage_textview)
    TextView mQuickStartImageUploadPercentageTextview;
    @Bind(R.id.quick_start_image_upload_progressbar)
    ProgressBar mQuickStartImageUploadProgressbar;
    @Bind(R.id.quick_start_image_upload_status_layout)
    LinearLayout mQuickStartImageUploadStatusLayout;
    @Bind(R.id.quick_start_image_view)
    ImageView mQuickStartImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mQuickStartImageUploadProgressbar.setVisibility(View.INVISIBLE);

    }

    //更新上传进度显示
    private void updateStatus(final double percentage) {
        long now = System.currentTimeMillis();//当前的时间
        long deltaTime = now - uploadLastTimePoint;//获取时间间隔
        long currentOffset = (long) (percentage * uploadFileLength);
        long deltaSize = currentOffset - uploadLastOffset;
        if (deltaTime <= 100) {
            return;
        }
        //根据文件长度的变化和时间变化,计算上传的速度
        final String speed = Tools.formatSpeed(deltaSize, deltaTime);
        // update
        uploadLastTimePoint = now;//更新历史的时间点
        uploadLastOffset = currentOffset;//当前的文件长度变成了历史的量了

        AsyncRun.run(new Runnable() {
            @Override
            public void run() {
                int progress = (int) (percentage * 100);
                mQuickStartImageUploadProgressbar.setProgress(progress);
                mQuickStartImageUploadPercentageTextview.setText(progress + " %");
                mQuickStartImageUploadSpeedTextview.setText(speed);
            }
        });
    }

    //4-
    private void upload(final String uploadToken, final String domain) {
        if (this.uploadManager == null) {
            this.uploadManager = new UploadManager();
        }
        //创建File对象,用于存储选择的照片
        File uploadFile = new File(this.uploadFilePath);

        /*****1 获取上传的进度的方法,进行一系列的数字准备*****/
        UploadOptions uploadOptions = new UploadOptions(null, null, false,
                new UpProgressHandler() {
                    //该方法会在文件上传的过程中持续进行,从而更新进度显示
                    @Override
                    public void progress(String key, double percent) {
                        updateStatus(percent);//更新上传的进度
                    }
                }, null);
        /*下面语句均为：初始化操作,仅执行一次*/
        final long startTime = System.currentTimeMillis();//开始上传的时间点
        final long fileLength = uploadFile.length();//获取上传文件的长度
        this.uploadFileLength = fileLength;
        this.uploadLastTimePoint = startTime;//更新历史时间点
        this.uploadLastOffset = 0;//开始上传时文件上传量为0
        // prepare status
        AsyncRun.run(new Runnable() {
            @Override
            public void run() {
                mQuickStartImageUploadPercentageTextview.setText("0 %");
                mQuickStartImageUploadSpeedTextview.setText("0 KB/s");
                mQuickStartImageUploadFileLengthTextview.setText(Tools.formatSize(fileLength));
                mQuickStartImageUploadStatusLayout.setVisibility(LinearLayout.VISIBLE);
            }
        });
        /***
         * 2 执行上传操作,同时更新上传进度
         * ***/
        this.uploadManager.put(uploadFile, null, uploadToken,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo respInfo,
                                         JSONObject jsonData) {
                        // reset status
                        //开始上传文件后,启动一个线程让进度界面显示,同时重置进度
                        AsyncRun.run(new Runnable() {
                            @Override
                            public void run() {
                                mQuickStartImageUploadStatusLayout
                                        .setVisibility(LinearLayout.INVISIBLE);
                                mQuickStartImageUploadProgressbar.setProgress(0);
                            }
                        });
                        //
                        long lastMillis = System.currentTimeMillis() - startTime;
                        //info:http请求的状态信息等,可记入日志,isOK()返回true表示上传成功
                        //jsonData:七牛反馈的信息,可从中解析保存在七牛服务的key等信息,具体字段取决上传策略的设置
                        if (respInfo.isOK()) {
                            try {
                                //获取图片上传后的保存在服务器后的key值
                                String fileKey = jsonData.getString("key");
                                //获取屏幕的信息
                                DisplayMetrics dm = new DisplayMetrics();
                                getWindowManager().getDefaultDisplay().getMetrics(dm);
                                final int width = dm.widthPixels;//屏幕的宽度

                                //图片上传后发起网络请求,将它从服务器端下载下来
                                final OkHttpClient httpClient = new OkHttpClient();
                                final String imageUrl = domain + "/" + fileKey + "?imageView2/0/w/" + width + "/format/jpg";

                                final URI imageUri = URI.create(imageUrl);
                                //通过访问链接得到主机的组件信息
                                final String host = imageUri.getHost();

                                final ImageView imageView = mQuickStartImageView;
                                final Request.Builder builder = new Request.Builder();
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String reqImageUrl = imageUrl;
                                        try {
                                            //获取主机对应的IP地址
                                            String ip = DomainUtils.getIpByDomain(host);
                                            if (ip != null) {
                                                //下面的方法是将链接进行格式化
                                                reqImageUrl = String.format("%s://%s%s",//指定的语言环境结构
                                                        imageUri.getScheme(), //获取uri链接的结构（http）
                                                        ip, //ip地址
                                                        imageUri.getPath()//url的解码地址
                                                );//将以上３个元素组合成　"s://ss"的形式,s代表每个元素

                                                //如果上面的链接还可以进行更深层次的查询,则将链接接着再进行格式化,尾部添上子查询
                                                if (imageUri.getQuery() != null) {
                                                    reqImageUrl = String.format("%s?%s",
                                                            reqImageUrl, // "s://ss"形式的访问链接
                                                            imageUri.getQuery()//更深一层的查询
                                                    );
                                                }
                                            }
                                            //创建请求连接
                                            Request request = builder.
                                                    url(reqImageUrl).addHeader("Host", host).//访问的链接
                                                    method("GET", null).build();//发起get请求
                                            //获得服务端返回的数据包
                                            Response response = httpClient.newCall(request).execute();
                                            //数据包获取成功后,将数据包解码成图片的形式
                                            if (response.isSuccessful()) {
                                                byte[] bytes = response.body().bytes();
                                                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                                AsyncRun.run(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        imageView.setImageBitmap(bitmap);
                                                    }
                                                });
                                            }
                                        } catch (IOException ex) {
                                        }
                                    }
                                }).start();
                            } catch (JSONException e) {
                                Log.e(LabConfig.LOG_TAG, e.getMessage());
                            }
                        } else {
                            Log.e(LabConfig.LOG_TAG, respInfo.toString());
                        }
                    }
                }
                , uploadOptions);//执行文件上传的过程,同时进行进度跟踪
    }

    //3-上传的初始化操作,获取访问的网络空间的身份令牌和域名空间
    public void uploadFile() {
        if (this.uploadFilePath == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final OkHttpClient httpClient = new OkHttpClient();
                Request req = new Request.Builder().url(
                        "http://115.231.183.102:9090/api/quick_start/simple_image_example_token.php"
                )//前缀的不同代表空间的不同,组合上固定的后缀,就是具体的访问的链接
                        .method("GET", null).build();//发送一个get请求
                Response resp = null;//服务器返回的数据
                try {
                    resp = httpClient.newCall(req).execute();//通过请求获得回应的数据
                    JSONObject jsonObject = new JSONObject(resp.body().string());//解析服务端返回的数据
                    String uploadToken = jsonObject.getString("uptoken");//身份识别的标志
                    String domain = jsonObject.getString("domain");//域名
                    upload(uploadToken, domain);
                } catch (Exception e) {
                    Log.e(LabConfig.LOG_TAG, e.getMessage());
                } finally {
                    if (resp != null) {
                        resp.body().close();
                    }
                }
            }
        }).start();
    }

    //2－选择文件子活动结束后返回到原来的活动,返回选择文件的路径
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE:
                // If the file selection was successful
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
                        try {
                            // Get the file path from the URI
                            final String path = FileUtils.getPath(this, uri);
                            this.uploadFilePath = path;//已经拿到子活动中选择的文件的路径了
                        } catch (Exception e) {
                            Toast.makeText(content,
                                    content.getString(R.string.get_upload_file_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //1-启动系统的选择文件的功能的子activity,选择自己本地的文件
    public void selectUploadFile() {
        Intent target = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(target,
                this.getString(R.string.choose_file));
        try {
            this.startActivityForResult(intent, REQUEST_CODE);//跳转到子活动,选择文件
        } catch (ActivityNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    @OnClick({R.id.bt_select, R.id.bt_upload})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_select:
                //选择文件
                selectUploadFile();
                break;
            case R.id.bt_upload:
                //上传文件
                uploadFile();
                break;
        }
    }


}
