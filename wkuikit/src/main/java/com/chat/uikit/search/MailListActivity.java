package com.chat.uikit.search;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActMailListLayoutBinding;
import com.chat.uikit.db.WKContactsDB;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.user.service.UserModel;
import com.chat.uikit.utils.PyingUtils;
import com.github.promeg.pinyinhelper.Pinyin;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.impl.LoadingPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MailListActivity extends WKBaseActivity<ActMailListLayoutBinding> {
    MailListAdapter adapter;
    private List<MailListEntity> allList;

    @Override
    protected ActMailListLayoutBinding getViewBinding() {
        return ActMailListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.maillist);
    }

    @Override
    protected void initPresenter() {
        String desc = String.format(getString(R.string.contact_permissions_des), getString(R.string.app_name));
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                if (result) {
                    getContacts();
                }
            }

            @Override
            public void clickResult(boolean isCancel) {
                finish();
            }
        }, this, desc, Manifest.permission.READ_CONTACTS);

    }

    @Override
    protected void initView() {
        adapter = new MailListAdapter();
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        // checkPermissions();
        adapter.addChildClickViewIds(R.id.addBtn);
        adapter.setOnItemChildClickListener((adapter1, view1, position) -> {
            MailListEntity entity = (MailListEntity) adapter1.getData().get(position);
            if (entity != null) {
                if (!TextUtils.isEmpty(entity.uid)) {
                    WKDialogUtils.getInstance().showInputDialog(MailListActivity.this, getString(R.string.apply), getString(R.string.input_remark), "", getString(R.string.input_remark), 20, text -> FriendModel.getInstance().applyAddFriend(entity.uid, entity.vercode, text, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            showToast(R.string.applyed);
                        } else showToast(msg);
                    }));

                } else {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("smsto:" + entity.phone));
                    intent.putExtra("sms_body", "我正在使用【悟空IM】app，体验还不错。你也赶快来下载玩玩吧！http://www.githubim.com");
                    startActivity(intent);
                }

            }
        });
        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(MailListActivity.this);
                return true;
            }
            return false;
        });
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchUser(editable.toString());
            }
        });
    }

    private void searchUser(String content) {
        if (WKReader.isEmpty(allList)) return;
        if (TextUtils.isEmpty(content)) {
            adapter.setList(allList);
            return;
        }
        List<MailListEntity> tempList = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            if ((!TextUtils.isEmpty(allList.get(i).name) && allList.get(i).name.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || !TextUtils.isEmpty(allList.get(i).phone) && allList.get(i).phone.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault()))) {
                tempList.add(allList.get(i));
            }
        }
        adapter.setList(tempList);
    }

    private void getContacts() {
        LoadingPopupView loadingPopup = new XPopup.Builder(this)
                .asLoading(getString(R.string.loading));
        loadingPopup.show();
        Observable.create((ObservableOnSubscribe<List<ContactEntity>>) e -> e.onNext(get())).observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribe(new Observer<>() {
            @Override
            public void onSubscribe(@NotNull Disposable d) {

            }

            @Override
            public void onNext(@NotNull List<ContactEntity> list) {
                loadingPopup.dismiss();
                if (WKReader.isNotEmpty(list)) {
                    List<MailListEntity> localList = WKContactsDB.getInstance().query();
                    List<MailListEntity> uploadList = new ArrayList<>();
                    if (WKReader.isNotEmpty(localList)) {
                        for (ContactEntity entity : list) {
                            boolean isAdd = true;
                            for (MailListEntity localEntity : localList) {
                                if (localEntity.phone.equals(entity.phone)) {
                                    isAdd = false;
                                    break;
                                }
                            }
                            if (isAdd) {
                                MailListEntity mailListEntity = new MailListEntity();
                                mailListEntity.name = entity.name;
                                mailListEntity.phone = entity.phone;
                                mailListEntity.zone = "";
                                uploadList.add(mailListEntity);
                            }
                        }
                    } else {
                        for (ContactEntity entity : list) {
                            MailListEntity mailListEntity = new MailListEntity();
                            mailListEntity.name = entity.name;
                            mailListEntity.phone = entity.phone;
                            mailListEntity.zone = "";
                            uploadList.add(mailListEntity);
                        }
                    }

                    if (WKReader.isNotEmpty(uploadList)) {
                        WKContactsDB.getInstance().save(uploadList);
                        UserModel.getInstance().uploadContacts(uploadList, (code, msg) -> {
                            if (code == HttpResponseCode.success) {
                                getMailList();
                            } else {
                                showToast(msg);
                                loadingPopup.dismiss();
                            }
                        });
                    } else {
                        loadingPopup.dismiss();
                        sort(localList);
                    }
                } else {
                    loadingPopup.dismiss();
                    showToast("手机无联系人");
                }

            }

            @Override
            public void onError(@NotNull Throwable e) {
                loadingPopup.dismiss();
            }

            @Override
            public void onComplete() {
                loadingPopup.dismiss();
            }
        });
    }

    private void getMailList() {
        UserModel.getInstance().getContacts((code, msg, list) -> {
            loadingPopup.dismiss();
            if (code == HttpResponseCode.success) {
                if (WKReader.isNotEmpty(list)) {
                    //  WKContactsDB.getInstance().save(list);
                    List<MailListEntity> localList = WKContactsDB.getInstance().query();
                    List<MailListEntity> allList = new ArrayList<>();
                    for (MailListEntity entity : localList) {
                        boolean isAdd = true;
                        for (MailListEntity entity1 : list) {
                            if (entity.phone.equals(entity1.phone)) {
                                isAdd = false;
                                break;
                            }
                        }
                        if (isAdd) allList.add(entity);
                    }
                    allList.addAll(0, list);
                    sort(allList);
                    WKContactsDB.getInstance().save(list);
                }
            } else {
                showToast(msg);
            }
        });
    }

    private void sort(List<MailListEntity> list) {
        if (WKReader.isEmpty(list)) return;
        List<MailListEntity> topList = new ArrayList<>();
        List<MailListEntity> otherList = new ArrayList<>();
        List<MailListEntity> letterList = new ArrayList<>();
        List<MailListEntity> numList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).name;
            if (!TextUtils.isEmpty(showName)) {
                if (PyingUtils.getInstance().isStartNum(showName)) {
                    list.get(i).pying = "#";
                } else
                    list.get(i).pying = Pinyin.toPinyin(showName, "").toUpperCase();
            } else list.get(i).pying = "#";
        }

        PyingUtils.getInstance().sortMailList(list);
        for (int i = 0, size = list.size(); i < size; i++) {
            if (!TextUtils.isEmpty(list.get(i).uid)) {
                topList.add(list.get(i));
            } else {
                if (PyingUtils.getInstance().isStartLetter(list.get(i).pying)) {
                    //字母
                    letterList.add(list.get(i));
                } else if (PyingUtils.getInstance().isStartNum(list.get(i).pying)) {
                    //数字
                    numList.add(list.get(i));
                } else otherList.add(list.get(i));
            }
        }
        allList = new ArrayList<>();
        allList.addAll(letterList);
        allList.addAll(numList);
        allList.addAll(otherList);
        if (WKReader.isNotEmpty(topList)) {
            MailListEntity entity = new MailListEntity();
            entity.itemType = 1;
            entity.pying = "1";
            allList.add(0, entity);
            allList.addAll(0, topList);
        }
        adapter.setList(allList);
    }

    @SuppressLint("Range")
    private List<ContactEntity> get() {
        // 号码
        String NUM = ContactsContract.CommonDataKinds.Phone.NUMBER;
        // 联系人姓名
        String NAME = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
        //联系人提供者的uri
        Uri phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;

        List<ContactEntity> list = new ArrayList<>();
        ContentResolver cr = MailListActivity.this.getContentResolver();
        Cursor cursor = cr.query(phoneUri, new String[]{NUM, NAME}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String phone = cursor.getString(cursor.getColumnIndex(NUM));
                String name = cursor.getString(cursor.getColumnIndex(NAME));
                ContactEntity contactEntity = new ContactEntity();
                if (!TextUtils.isEmpty(name)) {
                    contactEntity.name = name.replaceAll(" ", "");
                }
                if (!TextUtils.isEmpty(phone)) {
                    contactEntity.phone = phone.replace(" ", "").replace("+", "00");
                }
                list.add(contactEntity);
            }
            cursor.close();
        }
        return list;
    }
}
