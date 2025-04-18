/**

Copyright 2013 Intel Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.intel.cosbench.driver.operator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;

import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.api.storage.StorageInterruptedException;
import com.intel.cosbench.bench.Result;
import com.intel.cosbench.bench.Sample;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.driver.util.ObjectPicker;
import com.intel.cosbench.service.AbortedException;

public class Lister extends AbstractOperator {

    public static final String OP_TYPE = "list";

    private ObjectPicker objPicker = new ObjectPicker();

    public Lister() {
        /* empty */
    }

    @Override
    protected void init(String id, int ratio, String division, Config config) {
        super.init(id, ratio, division, config);
        objPicker.init4Lister(division, config);
    }

    @Override
    public String getOpType() {
        return OP_TYPE;
    }

    @Override
    protected void operate(int idx, int all, Session session) {
        String[] path = objPicker.pickTargetPath(session.getRandom(), idx, all);
        NullOutputStream out = new NullOutputStream();
        Sample sample = doList(out, path[0], path[1], config, session);
        session.getListener().onSampleCreated(sample);
        Date now = sample.getTimestamp();
        Result result = new Result(now, getId(), getOpType(), getSampleType(),
                getName(), sample.isSucc());
        session.getListener().onOperationCompleted(result);
    }

    private Sample doList(OutputStream out, String conName, String objName,
            Config config, Session session) {
        if (Thread.interrupted())
            throw new AbortedException();

        InputStream in = null;
        CountingOutputStream cout = new CountingOutputStream(out);
        long start = System.nanoTime();
        long xferTime = 0L;
        try {
            doLogDebug(session.getLogger(), "worker "+ session.getIndex() + " List target " + conName + "/" + objName);
            
            in = session.getApi().getList(conName, objName, config);
            long xferStart = System.nanoTime();
            copyLarge(in, cout);
            xferTime = (System.nanoTime() - xferStart) / 1000000;
        } catch (StorageInterruptedException sie) {
            doLogErr(session.getLogger(), sie.getMessage(), sie);
            throw new AbortedException();
        } catch (StorageException se) {
        	String msg = "List failed: " + conName + "/" + objName;
			doLogWarn(session.getLogger(), msg, se);
			
			return new Sample(new Date(), getId(), getOpType(), getSampleType(), getName(), false);
		} catch (Exception e) { // TODO: catch IOException, need to improve.
            isUnauthorizedException(e, session);
            errorStatisticsHandle(e, session, conName + "/" + objName);

            return new Sample(new Date(), getId(), getOpType(), getSampleType(), getName(), false);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(cout);
        }
        long end = System.nanoTime();

        return new Sample(new Date(), getId(), getOpType(), getSampleType(),
                getName(), true, (end - start) / 1000000, xferTime, cout.getByteCount());
    }

    public OutputStream copyLarge(InputStream input, OutputStream output)
            throws IOException {
        IOUtils.copyLarge(input, output);

        return output;
    }

}
