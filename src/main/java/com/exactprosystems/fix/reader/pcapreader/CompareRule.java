/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactprosystems.fix.reader.pcapreader;

import java.util.ArrayList;
import java.util.List;

public class CompareRule {
    public class Ratio {
        String primalValue;
        String mirrorValue;

        public Ratio(String primalValue, String mirrorValue) {
            this.primalValue = primalValue;
            this.mirrorValue = mirrorValue;
        }

        public String getPrimalValue() {
            return primalValue;
        }

        public void setPrimalValue(String primalValue) {
            this.primalValue = primalValue;
        }

        public String getMirrorValue() {
            return mirrorValue;
        }

        public void setMirrorValue(String mirrorValue) {
            this.mirrorValue = mirrorValue;
        }
    }

    private List<Ratio> ratioList = new ArrayList<>();
    private boolean ignoreCase;
    private boolean trim;

    public List<Ratio> getRatioList() {
        return ratioList;
    }

    public void setRatioList(List<Ratio> ratioList) {
        this.ratioList = ratioList;
    }

    public void addEmptyRatio() {
        ratioList.add(new Ratio("", ""));
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public boolean isTrim() {
        return trim;
    }

    public void setTrim(boolean trim) {
        this.trim = trim;
    }
}
