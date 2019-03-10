/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mm.arscutil;

import com.tencent.matrix.javalib.util.Log;
import com.tencent.mm.arscutil.data.ArscConstants;
import com.tencent.mm.arscutil.data.ResChunk;
import com.tencent.mm.arscutil.data.ResPackage;
import com.tencent.mm.arscutil.data.ResTable;
import com.tencent.mm.arscutil.data.ResType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jinqiuchen on 18/7/29.
 */

public class ArscUtil {

    private static final String TAG = "ArscUtil.ArscUtil";

    public static String resolveStringPoolEntry(byte[] buffer, Charset charSet) {
        ByteBuffer lenBuf = ByteBuffer.allocate(2);
        lenBuf.order(ByteOrder.LITTLE_ENDIAN);
        lenBuf.clear();
        lenBuf.put(buffer, 0, 2);
        lenBuf.flip();
        short len = lenBuf.getShort();
        if ((len & 0x80) != 0) {
            if (charSet.equals(StandardCharsets.UTF_8)) {
                return new String(buffer, 4, buffer.length - 4 - 1, charSet);
            } else {
                return new String(buffer, 4, buffer.length - 4, charSet);
            }
        } else {
            if (charSet.equals(StandardCharsets.UTF_8)) {
                return new String(buffer, 2, buffer.length - 2 - 1, charSet);
            } else {
                return new String(buffer, 2, buffer.length - 2, charSet);
            }
        }
    }

    public static String toUTF16String(byte[] buffer) {
        CharBuffer charBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer();
        int index = 0;
        for (; index < charBuffer.length(); index++) {
            if (charBuffer.get() == 0x00) {
                break;
            }
        }
        charBuffer.limit(index).position(0);
        return charBuffer.toString();
    }

    public static int getPackageId(int resourceId) {
        return (resourceId & 0xFF000000) >> 24;
    }

    public static int getResourceTypeId(int resourceId) {
        return (resourceId & 0x00FF0000) >> 16;
    }

    public static int getResourceEntryId(int resourceId) {
        return resourceId & 0x0000FFFF;
    }

    public static ResPackage findResPackage(ResTable resTable, int packageId) {
        ResPackage resPackage = null;
        for (ResPackage pkg : resTable.getPackages()) {
            if (pkg.getId() == packageId) {
                resPackage = pkg;
                break;
            }
        }
        return resPackage;
    }

    public static List<ResType> findResType(ResPackage resPacakge, int resourceId) {
        ResType resType = null;
        int typeId = (resourceId & 0X00FF0000) >> 16;
        int entryId = resourceId & 0x0000FFFF;
        List<ResType> resTypeList = new ArrayList<ResType>();
        List<ResChunk> resTypeArray = resPacakge.getResTypeArray();
        if (resTypeArray != null) {
            for (int i = 0; i < resTypeArray.size(); i++) {
                if (resTypeArray.get(i).getType() == ArscConstants.RES_TABLE_TYPE_TYPE
                        && ((ResType) resTypeArray.get(i)).getId() == typeId) {
                    int entryCount = ((ResType) resTypeArray.get(i)).getEntryCount();
                    if (entryId < entryCount) {
                        int offset = ((ResType) resTypeArray.get(i)).getEntryOffsets().get(entryId);
                        if (offset != ArscConstants.NO_ENTRY_INDEX) {
                            resType = ((ResType) resTypeArray.get(i));
                            resTypeList.add(resType);
                        }
                    }
                }
            }
        }
        return resTypeList;
    }

    public static void removeResource(ResTable resTable, int resourceId, String resourceName) throws IOException {
        ResPackage resPackage = findResPackage(resTable, getPackageId(resourceId));
        if (resPackage != null) {
            List<ResType> resTypeList = findResType(resPackage, resourceId);
            for (ResType resType : resTypeList) {
                int entryId = getResourceEntryId(resourceId);
                Log.i(TAG, "try to remove %s (%H), find resource %s", resourceName, resourceId, ArscUtil.resolveStringPoolEntry(resPackage.getResNamePool().getStrings().get(resType.getEntryTable().get(entryId).getStringPoolIndex()).array(), resPackage.getResNamePool().getCharSet()));
                resType.getEntryTable().set(entryId, null);
                resType.getEntryOffsets().set(entryId, ArscConstants.NO_ENTRY_INDEX);
                resType.refresh();
            }
            resPackage.refresh();
            resTable.refresh();
        }
    }

}
