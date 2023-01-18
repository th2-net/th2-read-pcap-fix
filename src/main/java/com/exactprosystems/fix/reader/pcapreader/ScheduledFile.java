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

import com.exactprosystems.fix.reader.pcapreader.constants.ScheduledFileStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

import static com.exactprosystems.fix.reader.pcapreader.constants.Constants.EPOCH_START_LOCAL;
import static com.exactprosystems.fix.reader.pcapreader.constants.Constants.KILO;
import static com.exactprosystems.fix.reader.pcapreader.constants.Constants.SECONDS_IN_MINUTE;
import static com.exactprosystems.fix.reader.pcapreader.constants.ScheduledFileStatus.ERROR;
import static com.exactprosystems.fix.reader.pcapreader.constants.ScheduledFileStatus.NOT_SYNCHRONIZED;
import static com.exactprosystems.fix.reader.pcapreader.constants.ScheduledFileStatus.SYNCHRONIZED;
import static com.exactprosystems.fix.reader.pcapreader.constants.ScheduledFileStatus.SYNCHRONIZING;

public class ScheduledFile {
    public final static int DEFAULT_CAPTURE_DAY = 0;
    public final static String CAPTURE_DAY_FIELD = "captureDay";

    private long id;
    private long folderId;
    private final String fileName;
    private String uid;
    private Date modified;
    private Date start;
    private Date lastProcessing;
    private ScheduledFileStatus status;
    private String error;
    private Long linesParsed;
    private Instant firstPacketTime;
    private Long bytesRead;
    private Long packetsRead;

    // The day the file was started, for PCAP - the 1st packet timestamp determines it.
    // For a file started at 2020-09-17T23:59:59.99 it's day 18522 (from EPOCH_START)
    private int captureDay; // TODO make nullable

    public ScheduledFile(String fileName) {
        this(fileName, NOT_SYNCHRONIZED, null);
    }

    public ScheduledFile(String fileName, ScheduledFileStatus status, String error) {
        this.fileName = fileName;
        this.status = status;
        this.error = error;
        captureDay = DEFAULT_CAPTURE_DAY;
    }

    public void markAsErroneous(String error) {
        status = ERROR;
        this.error = error;
    }

    public void markAsNotSynchronized() {
        status = NOT_SYNCHRONIZED;
        error = null;
    }

    public void markAsSynchronizing() {
        status = SYNCHRONIZING;
        error = null;
    }

    public void markAsSynchronized() {
        status = SYNCHRONIZED;
        error = null;
    }

    public String getStatusText() {
        return status.getDescription() + (status == ERROR ? error : "");
    }

    public String getDurationText() {
        if (start != null && lastProcessing != null) {
            long seconds = ChronoUnit.SECONDS.between(start.toInstant(), lastProcessing.toInstant());
            if (seconds == 0) {
                return "< 1 sec";
            }
            if (seconds < SECONDS_IN_MINUTE) {
                return seconds + " sec";
            }
            return (seconds / SECONDS_IN_MINUTE) + " min " + (seconds % SECONDS_IN_MINUTE) + " sec";
        }
        return "";
    }

    public String getFileSizeText() {
        if (bytesRead == null) {
            return "";
        }
        if (bytesRead == 0L) {
            return "0 bytes";
        }
        long displayValue = bytesRead;
        if (displayValue < 10_000L) {
            return displayValue + " B";
        }
        displayValue = (bytesRead + KILO / 2) / KILO;
        if (displayValue < 10_000L) {
            return displayValue + " kB";
        }
        displayValue = (bytesRead + KILO * KILO / 2) / KILO / KILO;
        return displayValue + " MB";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScheduledFile that = (ScheduledFile) o;
        return Objects.equals(fileName, that.fileName)
                && Objects.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, uid);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getFolderId() {
        return folderId;
    }

    public void setFolderId(long folderId) {
        this.folderId = folderId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date date) {
        modified = date;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getLastProcessing() {
        return lastProcessing;
    }

    public void setLastProcessing(Date lastProcessing) {
        this.lastProcessing = lastProcessing;
    }

    public ScheduledFileStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Long getLinesParsed() {
        return linesParsed;
    }

    public void setLinesParsed(Long linesParsed) {
        this.linesParsed = linesParsed;
    }

    public Instant getFirstPacketTime() {
        return firstPacketTime;
    }

    public void setFirstPacketTime(Instant firstPacketTime) {
        this.firstPacketTime = firstPacketTime;
    }

    public Integer getCaptureDay() {
        return captureDay;
    }

    public void setDefaultCaptureDay() {
        captureDay = DEFAULT_CAPTURE_DAY;
    }

    public void setCaptureDay(Instant timeStamp, ZoneId tz) {
        ZonedDateTime zdt0 = ZonedDateTime.of(EPOCH_START_LOCAL, tz);
        captureDay = (int) ChronoUnit.DAYS.between(zdt0, timeStamp.atZone(tz)); // 2020-09-19 is day 18524
    }

    public Long getBytesRead() {
        return bytesRead;
    }

    public void setBytesRead(Long bytesRead) {
        this.bytesRead = bytesRead;
    }

    public Long getPacketsRead() {
        return packetsRead;
    }

    public void setPacketsRead(Long packetsRead) {
        this.packetsRead = packetsRead;
    }
}
