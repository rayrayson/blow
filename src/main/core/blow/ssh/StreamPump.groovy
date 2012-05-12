/*
 * Copyright (c) 2012, the authors.
 *
 *   This file is part of Blow.
 *
 *   Blow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Blow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
 */

package blow.ssh

import net.schmizz.concurrent.Event
import net.schmizz.concurrent.ExceptionChainer

/**
 * This class copy a source input stream to a target output stream
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * Date: 4/23/12 6:23 PM
 */


class StreamPump {

    private InputStream input;
    private OutputStream output;

    private int bufSize = 1;
    private boolean keepFlushing = true;
    private long length = -1;
    private boolean abort;

    private Thread thread

    public StreamPump( InputStream source, OutputStream target ) {
        this.input = source;
        this.output = target;
    }

    public Event<IOException> spawn(String name) {
        return spawn(name, false);
    }

    public Event<IOException> spawnDaemon(String name) {
        return spawn(name, true);
    }

    private Event<IOException> spawn(String name, boolean daemon) {
        final Event<IOException> doneEvent =
            new Event<IOException>("copyDone", new ExceptionChainer<IOException>() {
                @Override
                public IOException chain(Throwable t) {
                    return (t instanceof IOException) ? (IOException) t : new IOException(t);
                }
            });

        thread = new Thread() {
            {
                setName(name);
                setDaemon(daemon);
            }

            @Override
            public void run() {
                try {
                    //log.debug("Will copy from {} to {}", in, output);
                    copy();
                    //log.debug("Done copying from {}", in);
                    doneEvent.set();
                } catch (IOException ioe) {
                    //log.error("In pipe from {} to {}: " + ioe.toString(), in, output);
                    doneEvent.deliverError(ioe);
                }
            }
        }
        thread.start();
        return doneEvent;

    }


    public long copy()
    throws IOException {
        final byte[] buf = new byte[bufSize];
        long count = 0;
        int read = 0;

        final long startTime = System.currentTimeMillis();

        if (length == -1) {
            while( !abort() && (read = input.read(buf)) != -1 )
            count = write(buf, count, read);
        } else {
            while( !abort() && count < length && (read = input.read(buf, 0, (int) Math.min(bufSize, length - count))) != -1)
            count = write(buf, count, read);
        }

        if (!keepFlushing)
            output.flush();

        //final double timeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        //final double sizeKiB = count / 1024.0;
        //log.info(sizeKiB + " KiB transferred  in {} seconds ({} KiB/s)", timeSeconds, (sizeKiB / timeSeconds));

        if (length != -1 && read == -1)
            throw new IOException("Encountered EOF, could not transfer " + length + " bytes");

        return count;
    }

    public StreamPump bufSize(int bufSize) {
        this.bufSize = bufSize;
        return this;
    }

    public StreamPump keepFlushing(boolean keepFlushing) {
        this.keepFlushing = keepFlushing;
        return this;
    }

    public StreamPump length(long length) {
        this.length = length;
        return this;
    }

    protected long write(byte[] buf, long count, int read)
    throws IOException {
        output.write(buf, 0, read);
        count += read;
        if (keepFlushing)
            output.flush();
        //listener.reportProgress(count);
        return count;
    }


    public void abort(boolean value) {
        this.abort = value;
    }

    public boolean abort() { return abort; }
}