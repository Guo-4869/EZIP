# Generates all Android resource XML for EZip (keeps the tree in one reviewable place).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res = Join-Path $root "app\src\main\res"
$enc = New-Object System.Text.UTF8Encoding($false)

$files = @{}

$files["values\colors.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#3B6FF6</color>
    <color name="primary_dark">#2E5BD6</color>
    <color name="primary_pale">#EAF1FE</color>
    <color name="bg">#F5F7FA</color>
    <color name="surface">#FFFFFF</color>
    <color name="text">#101828</color>
    <color name="text2">#667085</color>
    <color name="text3">#98A2B3</color>
    <color name="divider">#EDF0F4</color>
    <color name="danger">#E5484D</color>
    <color name="ok">#12B76A</color>
    <color name="badge_image">#12B76A</color>
    <color name="badge_video">#F04438</color>
    <color name="badge_audio">#7A5AF8</color>
    <color name="badge_pdf">#E5484D</color>
    <color name="badge_doc">#2E6BE6</color>
    <color name="badge_ppt">#E8590C</color>
    <color name="badge_xls">#0E9384</color>
    <color name="badge_archive">#B54708</color>
    <color name="badge_text">#475467</color>
    <color name="badge_apk">#12B76A</color>
    <color name="badge_code">#6941C6</color>
    <color name="badge_generic">#98A2B3</color>
</resources>
'@

$files["values\strings.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">EZip</string>
    <string name="storage">内部存储</string>

    <string name="sort">排序</string>
    <string name="more">更多</string>
    <string name="select_all">全选</string>
    <string name="cancel">取消</string>
    <string name="confirm">确定</string>
    <string name="done">完成</string>
    <string name="compress">压缩</string>
    <string name="extract">解压</string>
    <string name="delete">删除</string>
    <string name="rename">重命名</string>
    <string name="new_folder">新建文件夹</string>
    <string name="refresh">刷新</string>
    <string name="show_hidden">显示隐藏文件</string>
    <string name="hide_hidden">隐藏隐藏文件</string>
    <string name="about">关于</string>
    <string name="open_with">用其他应用打开</string>
    <string name="open">打开</string>
    <string name="selected_count">已选 %1$d 项</string>

    <string name="empty_dir">此文件夹为空</string>
    <string name="perm_title">需要文件访问权限</string>
    <string name="perm_desc">授予「所有文件访问权限」后，EZip 才能浏览手机文件并压缩、解压任意目录。</string>
    <string name="perm_button">去开启权限</string>

    <string name="sort_name">按名称</string>
    <string name="sort_size">按大小</string>
    <string name="sort_date">按时间</string>

    <string name="compress_title">压缩为压缩包</string>
    <string name="compress_name">文件名</string>
    <string name="compress_format">格式</string>
    <string name="compress_format_zip">ZIP（兼容性最好）</string>
    <string name="compress_format_7z">7Z（压缩率更高）</string>
    <string name="compress_level">压缩级别</string>
    <string name="level_store">仅存储</string>
    <string name="level_normal">标准</string>
    <string name="level_max">最大</string>
    <string name="compress_password">加密密码（可选）</string>
    <string name="password_hint">留空表示不加密</string>
    <string name="start_compress">开始压缩</string>

    <string name="extract_title">解压</string>
    <string name="extract_target">解压到</string>
    <string name="extract_password">密码</string>
    <string name="extract_password_hint">压缩包有密码时填写</string>
    <string name="start_extract">开始解压</string>
    <string name="extract_here">解压到当前目录</string>
    <string name="extract_to">解压到…</string>
    <string name="view_content">查看内容</string>
    <string name="extract_all">全部解压</string>
    <string name="entries_count">%1$d 个文件</string>

    <string name="preparing">准备中…</string>
    <string name="work_cancel">取消</string>
    <string name="preview_unsupported">无法预览此文件</string>
    <string name="preview_unsupported_desc">可以使用其他应用打开它。</string>
    <string name="loading">加载中…</string>
    <string name="page_of">第 %1$d / %2$d 页</string>

    <string name="delete_confirm_title">删除确认</string>
    <string name="delete_confirm_msg">确定删除 %1$d 项？此操作不可恢复。</string>
    <string name="new_folder_title">新建文件夹</string>
    <string name="name_hint">请输入名称</string>
    <string name="rename_title">重命名</string>
    <string name="about_msg">EZip 1.0.0\n\n一个开源、免费、无广告的 Android 压缩解压工具。\n\n支持解压：ZIP / RAR / 7Z / TAR / GZ / BZ2 / XZ\n支持压缩：ZIP（AES-256 加密）/ 7Z（AES-256 加密）\n支持预览：图片、文本、PDF、视频、音频、Word、PPT、Excel</string>

    <string name="err_permission">没有权限访问该目录</string>
    <string name="err_no_selection">请先选择文件</string>
    <string name="err_single_archive">请只选择一个压缩包进行解压</string>
    <string name="err_empty_name">名称不能为空</string>
    <string name="err_password">密码错误或压缩包已损坏</string>
    <string name="done_compress">压缩完成</string>
    <string name="done_extract">解压完成</string>
    <string name="done_delete">已删除</string>
</resources>
'@

$files["values\styles.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/bg</item>
        <item name="android:statusBarColor">@color/primary_dark</item>
        <item name="android:navigationBarColor">@color/surface</item>
        <item name="android:colorAccent">@color/primary</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/text2</item>
    </style>

    <style name="DialogTheme" parent="@android:style/Theme.Material.Light.Dialog.Alert">
        <item name="android:windowBackground">@drawable/bg_dialog</item>
        <item name="android:colorAccent">@color/primary</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/text2</item>
        <item name="android:windowMinWidthMajor">86%</item>
        <item name="android:windowMinWidthMinor">86%</item>
    </style>

    <style name="Label">
        <item name="android:textSize">13sp</item>
        <item name="android:textColor">@color/text2</item>
    </style>

    <style name="ButtonPrimary">
        <item name="android:background">@drawable/btn_primary</item>
        <item name="android:textColor">#FFFFFF</item>
        <item name="android:textSize">15sp</item>
        <item name="android:gravity">center</item>
        <item name="android:paddingLeft">18dp</item>
        <item name="android:paddingRight">18dp</item>
        <item name="android:paddingTop">10dp</item>
        <item name="android:paddingBottom">10dp</item>
        <item name="android:clickable">true</item>
    </style>

    <style name="ButtonGhost">
        <item name="android:background">@drawable/btn_ghost</item>
        <item name="android:textColor">@color/primary</item>
        <item name="android:textSize">15sp</item>
        <item name="android:gravity">center</item>
        <item name="android:paddingLeft">18dp</item>
        <item name="android:paddingRight">18dp</item>
        <item name="android:paddingTop">10dp</item>
        <item name="android:paddingBottom">10dp</item>
        <item name="android:clickable">true</item>
    </style>

    <style name="Input">
        <item name="android:background">@drawable/bg_input</item>
        <item name="android:textSize">15sp</item>
        <item name="android:textColor">@color/text</item>
        <item name="android:textColorHint">@color/text3</item>
        <item name="android:paddingLeft">12dp</item>
        <item name="android:paddingRight">12dp</item>
        <item name="android:paddingTop">10dp</item>
        <item name="android:paddingBottom">10dp</item>
        <item name="android:singleLine">true</item>
    </style>
</resources>
'@

$files["values\ic_launcher_background.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#3B6FF6</color>
</resources>
'@

# ---------- drawables ----------
$files["drawable\bg_dialog.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface" />
    <corners android:radius="18dp" />
</shape>
'@

$files["drawable\bg_card.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface" />
    <corners android:radius="14dp" />
</shape>
'@

$files["drawable\bg_input.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#F7F9FC" />
    <corners android:radius="10dp" />
    <stroke android:width="1dp" android:color="#E4E8EF" />
</shape>
'@

$files["drawable\btn_primary.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/primary_dark" />
            <corners android:radius="12dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/primary" />
            <corners android:radius="12dp" />
        </shape>
    </item>
</selector>
'@

$files["drawable\btn_ghost.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#DCE7FD" />
            <corners android:radius="12dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/primary_pale" />
            <corners android:radius="12dp" />
        </shape>
    </item>
</selector>
'@

$files["drawable\item_bg.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#F0F4FB" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/surface" />
        </shape>
    </item>
</selector>
'@

$files["drawable\bg_pill.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#33FFFFFF" />
    <corners android:radius="20dp" />
</shape>
'@

# icons (24dp, stroke or fill)
function Icon-Stroke($path, $width = "2") {
    return ('<?xml version="1.0" encoding="utf-8"?>' + "`n" +
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + "`n" +
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">' + "`n" +
        '    <path android:pathData="' + $path + '" android:strokeColor="#FF000000" android:strokeWidth="' + $width + '"' + "`n" +
        '        android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" />' + "`n" +
        '</vector>' + "`n")
}
function Icon-Fill($path) {
    return ('<?xml version="1.0" encoding="utf-8"?>' + "`n" +
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + "`n" +
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">' + "`n" +
        '    <path android:pathData="' + $path + '" android:fillColor="#FF000000" />' + "`n" +
        '</vector>' + "`n")
}

$files["drawable\ic_back.xml"]     = Icon-Stroke "M15,5 L8,12 L15,19" "2.2"
$files["drawable\ic_close.xml"]    = Icon-Stroke "M6,6 L18,18 M18,6 L6,18" "2.2"
$files["drawable\ic_up.xml"]       = Icon-Stroke "M12,19 L12,6 M6,12 L12,6 L18,12" "2"
$files["drawable\ic_sort.xml"]     = Icon-Stroke "M4,7 L17,7 M4,12 L13,12 M4,17 L9,17 M18,11 L18,20 M15,17 L18,20 L21,17" "1.8"
$files["drawable\ic_check.xml"]    = Icon-Stroke "M5,12.6 L9.6,17.2 L19,7.6" "2.4"
$files["drawable\ic_plus.xml"]     = Icon-Stroke "M12,5 L12,19 M5,12 L19,12" "2.2"
$files["drawable\ic_trash.xml"]    = Icon-Stroke "M4,7 L20,7 M9.5,7 L9.5,4.6 L14.5,4.6 L14.5,7 M6.5,7 L7.5,19.5 L16.5,19.5 L17.5,7 M10,10.5 L10,16.5 M14,10.5 L14,16.5" "1.8"
$files["drawable\ic_open.xml"]     = Icon-Stroke "M14,4 L20,4 L20,10 M20,4 L12,12 M18,14 L18,19 L5,19 L5,6 L10,6" "1.8"
$files["drawable\ic_more.xml"]     = Icon-Fill  "M10.8,5.2 h2.4 v2.4 h-2.4 z M10.8,10.8 h2.4 v2.4 h-2.4 z M10.8,16.4 h2.4 v2.4 h-2.4 z"
$files["drawable\ic_folder.xml"]   = Icon-Fill  "M3.2,6.2 a1.6,1.6 0 0 1 1.6,-1.6 h4.3 l1.7,2 h8 a1.6,1.6 0 0 1 1.6,1.6 v9.6 a1.6,1.6 0 0 1 -1.6,1.6 h-14 a1.6,1.6 0 0 1 -1.6,-1.6 z"
$files["drawable\ic_check_circle.xml"] = Icon-Fill "M12,2 a10,10 0 1 0 0,20 a10,10 0 0 0 0,-20 z M10.6,16.4 L6.2,12 l1.5,-1.5 l2.9,2.9 l6,-6 l1.5,1.5 z"
$files["drawable\ic_archive.xml"] = Icon-Stroke "M4,8 L20,8 L20,19 L4,19 z M3,8 L3,6 L21,6 L21,8 M10.5,6 L10.5,3 L13.5,3 L13.5,6 M10.5,12 L13.5,12 M10.5,12 L13.5,12 M10.5,15.5 L13.5,15.5" "1.7"
$files["drawable\ic_compress.xml"] = Icon-Stroke "M12,10 L12,3 M9,6 L12,3 L15,6 M6,13 L18,13 L18,20 L6,20 z" "1.9"
$files["drawable\ic_extract.xml"]  = Icon-Stroke "M12,3 L12,10 M9,7 L12,10 L15,7 M6,13 L18,13 L18,20 L6,20 z" "1.9"
$files["drawable\ic_chevron.xml"]  = Icon-Stroke "M9,6 L15,12 L9,18" "2"
$files["drawable\ic_lock.xml"]     = Icon-Stroke "M6,11 L18,11 L18,20 L6,20 z M9,11 L9,7.5 a3,3 0 0 1 6,0 L15,11" "1.7"
$files["drawable\ic_play.xml"]     = Icon-Fill  "M8,5 L19,12 L8,19 z"
$files["drawable\ic_pause.xml"]    = Icon-Fill  "M7,5 h4 v14 h-4 z M13,5 h4 v14 h-4 z"

$files["drawable\ic_launcher_foreground.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M36,30 h26 l12,12 v36 a4,4 0 0 1 -4,4 h-34 a4,4 0 0 1 -4,-4 v-44 a4,4 0 0 1 4,-4 z"
        android:fillColor="#FFFFFFFF" />
    <path android:pathData="M62,30 l12,12 h-12 z" android:fillColor="#FFC8D8FF" />
    <path android:pathData="M46,50 h12 v4 h-12 z M46,57 h12 v4 h-12 z M46,64 h12 v4 h-12 z"
        android:fillColor="#FF3B6FF6" />
</vector>
'@

# ---------- mipmap ----------
$files["mipmap-anydpi-v26\ic_launcher.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'@

# ---------- layouts ----------
$files["layout\activity_main.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg"
    android:orientation="vertical">

    <!-- 常规标题栏 -->
    <LinearLayout
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:background="@color/primary"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="16dp"
        android:paddingRight="8dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/app_name"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/btnSort"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/sort"
            android:padding="9dp"
            android:src="@drawable/ic_sort"
            android:tint="#FFFFFF" />

        <ImageView
            android:id="@+id/btnMore"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/more"
            android:padding="12dp"
            android:src="@drawable/ic_more"
            android:tint="#FFFFFF" />
    </LinearLayout>

    <!-- 多选标题栏 -->
    <LinearLayout
        android:id="@+id/toolbarSelect"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:background="@color/primary"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="16dp"
        android:paddingRight="8dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/txtSelCount"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="#FFFFFF"
            android:textSize="17sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/btnSelectAll"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:padding="10dp"
            android:text="@string/select_all"
            android:textColor="#FFFFFF"
            android:textSize="15sp" />

        <ImageView
            android:id="@+id/btnSelClose"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/cancel"
            android:padding="11dp"
            android:src="@drawable/ic_close"
            android:tint="#FFFFFF" />
    </LinearLayout>

    <!-- 路径栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="44dp"
        android:background="@color/surface"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="4dp"
        android:paddingRight="12dp">

        <ImageView
            android:id="@+id/btnUp"
            android:layout_width="38dp"
            android:layout_height="38dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/app_name"
            android:padding="9dp"
            android:src="@drawable/ic_up"
            android:tint="@color/text2" />

        <TextView
            android:id="@+id/txtPath"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="start"
            android:singleLine="true"
            android:textColor="@color/text2"
            android:textSize="13sp" />
    </LinearLayout>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/divider" />

    <!-- 权限提示 -->
    <LinearLayout
        android:id="@+id/permCard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:background="@drawable/bg_card"
        android:orientation="vertical"
        android:padding="18dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/perm_title"
            android:textColor="@color/text"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:lineSpacingExtra="4dp"
            android:text="@string/perm_desc"
            android:textColor="@color/text2"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/btnGrant"
            style="@style/ButtonPrimary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:text="@string/perm_button" />
    </LinearLayout>

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <ListView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:divider="@color/divider"
            android:dividerHeight="1dp"
            android:fastScrollEnabled="true"
            android:scrollbars="vertical" />

        <LinearLayout
            android:id="@+id/emptyView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:visibility="gone">

            <ImageView
                android:layout_width="52dp"
                android:layout_height="52dp"
                android:src="@drawable/ic_archive"
                android:tint="@color/text3" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/empty_dir"
                android:textColor="@color/text3"
                android:textSize="14sp" />
        </LinearLayout>
    </FrameLayout>

    <!-- 底部操作栏 -->
    <LinearLayout
        android:id="@+id/actionBar"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:background="@color/surface"
        android:elevation="8dp"
        android:orientation="horizontal"
        android:visibility="gone">

        <LinearLayout
            android:id="@+id/actCompress"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?android:attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="22dp"
                android:layout_height="22dp"
                android:src="@drawable/ic_compress"
                android:tint="@color/primary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:text="@string/compress"
                android:textColor="@color/primary"
                android:textSize="12sp" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/actExtract"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?android:attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="22dp"
                android:layout_height="22dp"
                android:src="@drawable/ic_extract"
                android:tint="@color/primary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:text="@string/extract"
                android:textColor="@color/primary"
                android:textSize="12sp" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/actDelete"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?android:attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="22dp"
                android:layout_height="22dp"
                android:src="@drawable/ic_trash"
                android:tint="@color/danger" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:text="@string/delete"
                android:textColor="@color/danger"
                android:textSize="12sp" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/actMore"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?android:attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="vertical">

            <ImageView
                android:layout_width="22dp"
                android:layout_height="22dp"
                android:src="@drawable/ic_more"
                android:tint="@color/text2" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:text="@string/more"
                android:textColor="@color/text2"
                android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
'@

$files["layout\item_file.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/item_bg"
    android:gravity="center_vertical"
    android:minHeight="62dp"
    android:orientation="horizontal"
    android:paddingLeft="16dp"
    android:paddingTop="10dp"
    android:paddingRight="14dp"
    android:paddingBottom="10dp">

    <FrameLayout
        android:layout_width="40dp"
        android:layout_height="40dp">

        <TextView
            android:id="@+id/badge"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:gravity="center"
            android:textColor="#FFFFFF"
            android:textSize="10sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/icon"
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:layout_gravity="center"
            android:src="@drawable/ic_folder"
            android:tint="@color/primary"
            android:visibility="gone" />
    </FrameLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginLeft="12dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="middle"
            android:maxLines="1"
            android:textColor="@color/text"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:maxLines="1"
            android:textColor="@color/text2"
            android:textSize="12sp" />
    </LinearLayout>

    <ImageView
        android:id="@+id/check"
        android:layout_width="22dp"
        android:layout_height="22dp"
        android:layout_marginLeft="8dp"
        android:src="@drawable/ic_check_circle"
        android:tint="@color/primary"
        android:visibility="gone" />

    <ImageView
        android:id="@+id/chevron"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:layout_marginLeft="6dp"
        android:src="@drawable/ic_chevron"
        android:tint="@color/text3"
        android:visibility="gone" />
</LinearLayout>
'@

$files["layout\item_entry.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/item_bg"
    android:gravity="center_vertical"
    android:minHeight="54dp"
    android:orientation="horizontal"
    android:paddingLeft="16dp"
    android:paddingTop="8dp"
    android:paddingRight="16dp"
    android:paddingBottom="8dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="22dp"
        android:layout_height="22dp"
        android:src="@drawable/ic_folder"
        android:tint="@color/primary" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginLeft="12dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="middle"
            android:maxLines="1"
            android:textColor="@color/text"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="1dp"
            android:maxLines="1"
            android:textColor="@color/text2"
            android:textSize="11sp" />
    </LinearLayout>
</LinearLayout>
'@

$files["layout\activity_archive.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:background="@color/primary"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="6dp"
        android:paddingRight="8dp">

        <ImageView
            android:id="@+id/btnBack"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/cancel"
            android:padding="10dp"
            android:src="@drawable/ic_back"
            android:tint="#FFFFFF" />

        <TextView
            android:id="@+id/txtTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginLeft="6dp"
            android:layout_weight="1"
            android:ellipsize="middle"
            android:maxLines="1"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/btnExtractAll"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_pill"
            android:paddingLeft="12dp"
            android:paddingTop="6dp"
            android:paddingRight="12dp"
            android:paddingBottom="6dp"
            android:text="@string/extract_all"
            android:textColor="#FFFFFF"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/txtInfo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/surface"
        android:paddingLeft="16dp"
        android:paddingTop="10dp"
        android:paddingRight="16dp"
        android:paddingBottom="10dp"
        android:textColor="@color/text2"
        android:textSize="12sp" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/divider" />

    <ListView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:divider="@color/divider"
        android:dividerHeight="1dp" />
</LinearLayout>
'@

$files["layout\activity_preview.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:background="@color/primary"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="6dp"
        android:paddingRight="8dp">

        <ImageView
            android:id="@+id/btnBack"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/cancel"
            android:padding="10dp"
            android:src="@drawable/ic_back"
            android:tint="#FFFFFF" />

        <TextView
            android:id="@+id/txtTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginLeft="6dp"
            android:layout_weight="1"
            android:ellipsize="middle"
            android:maxLines="1"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/btnOpenWith"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/open_with"
            android:padding="10dp"
            android:src="@drawable/ic_open"
            android:tint="#FFFFFF" />
    </LinearLayout>

    <FrameLayout
        android:id="@+id/content"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <LinearLayout
        android:id="@+id/bottomBar"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:background="@color/surface"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="8dp"
        android:paddingRight="8dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/btnPrev"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:padding="9dp"
            android:src="@drawable/ic_back"
            android:tint="@color/text2" />

        <TextView
            android:id="@+id/txtPage"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:gravity="center"
            android:textColor="@color/text"
            android:textSize="13sp" />

        <ImageView
            android:id="@+id/btnNext"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:padding="9dp"
            android:rotation="180"
            android:src="@drawable/ic_back"
            android:tint="@color/text2" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/audioBar"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:background="@color/surface"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingLeft="12dp"
        android:paddingRight="12dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/btnPlay"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:padding="10dp"
            android:src="@drawable/ic_play"
            android:tint="@color/primary" />

        <SeekBar
            android:id="@+id/seek"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginLeft="8dp"
            android:layout_weight="1"
            android:max="1000" />

        <TextView
            android:id="@+id/txtTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginLeft="8dp"
            android:textColor="@color/text2"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>
'@

$files["layout\dialog_compress.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:maxHeight="520dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="20dp"
        android:paddingTop="20dp"
        android:paddingRight="20dp"
        android:paddingBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/compress_title"
            android:textColor="@color/text"
            android:textSize="17sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/txtSummary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="@color/text2"
            android:textSize="12sp" />

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/compress_name" />

        <EditText
            android:id="@+id/editName"
            style="@style/Input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:inputType="text"
            android:maxLines="1" />

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:text="@string/compress_format" />

        <RadioGroup
            android:id="@+id/radioFormat"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:orientation="vertical">

            <RadioButton
                android:id="@+id/fmtZip"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:checked="true"
                android:text="@string/compress_format_zip"
                android:textColor="@color/text"
                android:textSize="14sp" />

            <RadioButton
                android:id="@+id/fmt7z"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/compress_format_7z"
                android:textColor="@color/text"
                android:textSize="14sp" />
        </RadioGroup>

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:text="@string/compress_level" />

        <RadioGroup
            android:id="@+id/radioLevel"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:orientation="horizontal">

            <RadioButton
                android:id="@+id/lvlStore"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/level_store"
                android:textColor="@color/text"
                android:textSize="14sp" />

            <RadioButton
                android:id="@+id/lvlNormal"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:checked="true"
                android:text="@string/level_normal"
                android:textColor="@color/text"
                android:textSize="14sp" />

            <RadioButton
                android:id="@+id/lvlMax"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/level_max"
                android:textColor="@color/text"
                android:textSize="14sp" />
        </RadioGroup>

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:text="@string/compress_password" />

        <EditText
            android:id="@+id/editPassword"
            style="@style/Input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:hint="@string/password_hint"
            android:inputType="textPassword"
            android:maxLines="1" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="22dp"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/btnCancel"
                style="@style/ButtonGhost"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/cancel" />

            <TextView
                android:id="@+id/btnStart"
                style="@style/ButtonPrimary"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginLeft="10dp"
                android:layout_weight="1"
                android:text="@string/start_compress" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'@

$files["layout\dialog_extract.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:maxHeight="520dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="20dp"
        android:paddingTop="20dp"
        android:paddingRight="20dp"
        android:paddingBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/extract_title"
            android:textColor="@color/text"
            android:textSize="17sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/txtSummary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="@color/text2"
            android:textSize="12sp" />

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/extract_target" />

        <EditText
            android:id="@+id/editTarget"
            style="@style/Input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:inputType="text"
            android:maxLines="1" />

        <TextView
            style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:text="@string/extract_password" />

        <EditText
            android:id="@+id/editPassword"
            style="@style/Input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:hint="@string/extract_password_hint"
            android:inputType="textPassword"
            android:maxLines="1" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="22dp"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/btnCancel"
                style="@style/ButtonGhost"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/cancel" />

            <TextView
                android:id="@+id/btnStart"
                style="@style/ButtonPrimary"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginLeft="10dp"
                android:layout_weight="1"
                android:text="@string/start_extract" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'@

$files["layout\dialog_progress.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_dialog"
    android:orientation="vertical"
    android:paddingLeft="22dp"
    android:paddingTop="22dp"
    android:paddingRight="22dp"
    android:paddingBottom="18dp">

    <TextView
        android:id="@+id/txtTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@color/text"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/txtMsg"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:ellipsize="middle"
        android:maxLines="1"
        android:textColor="@color/text2"
        android:textSize="13sp" />

    <ProgressBar
        android:id="@+id/progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:max="1000"
        android:progress="0" />

    <TextView
        android:id="@+id/txtDetail"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:textColor="@color/text3"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/btnCancel"
        style="@style/ButtonGhost"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:layout_marginTop="16dp"
        android:text="@string/work_cancel" />
</LinearLayout>
'@

$files["layout\dialog_menu.xml"] = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <TextView
        android:id="@+id/txtTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="middle"
        android:maxLines="1"
        android:paddingLeft="20dp"
        android:paddingTop="8dp"
        android:paddingRight="20dp"
        android:paddingBottom="10dp"
        android:textColor="@color/text2"
        android:textSize="13sp"
        android:visibility="gone" />

    <ListView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:divider="@null"
        android:maxHeight="420dp" />
</LinearLayout>
'@

foreach ($rel in $files.Keys) {
    $path = Join-Path $res $rel
    $dir = Split-Path -Parent $path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $text = $files[$rel] -replace "^\r?\n", ""
    [System.IO.File]::WriteAllText($path, $text, $enc)
    Write-Output "wrote $rel"
}
Write-Output "resources: $($files.Count) files"
