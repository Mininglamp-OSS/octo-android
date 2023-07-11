package com.chat.login;

import com.chat.login.entity.CountryCodeEntity;

import java.util.List;

/**
 * 2020-08-03 21:56
 */
public class PyUtils {
    private PyUtils() {
    }

    private static class PyUtilsBinder {
        final static PyUtils pyUtils = new PyUtils();
    }

    public static PyUtils getInstance() {
        return PyUtilsBinder.pyUtils;
    }


    public void sortListBasic(List<CountryCodeEntity> list) {
        transferListBasic(list);
    }

    /**
     * 进行冒泡排序
     *
     * @param list List<CountryCodeEntity>
     */
    private void transferListBasic(List<CountryCodeEntity> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                exchangeNameOrderBasic(j, list);
            }
        }
    }

    /**
     * 交换两个名字的顺序,根据首字母判断
     *
     * @param j    int
     * @param list List<CountryCodeEntity>
     */
    private void exchangeNameOrderBasic(int j, List<CountryCodeEntity> list) {
        String namePinYin1 = list.get(j).pying;
        String namePinYin2 = list.get(j + 1).pying;

        int size = Math.min(namePinYin1.length(), namePinYin2.length());
        for (int i = 0; i < size; i++) {
            char jc = namePinYin1.charAt(i);
            char jcNext = namePinYin2.charAt(i);
            if (jc < jcNext) {//A在B之前就不用比较了
                break;
            }
            if (jc > jcNext) {//A在B之后就直接交换,让A在前面B在后面
                CountryCodeEntity nameBean = list.get(j);
                list.set(j, list.get(j + 1));
                list.set(j + 1, nameBean);
                break;
            }
            //如果AB一样就继续比较后面的字母
        }
    }
}
