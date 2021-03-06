/**
 * This file is part of Erjang - A JVM-based Erlang VM
 *
 * Copyright (c) 2009 by Trifork
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
 **/

package erjang;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import kilim.Pausable;
import erjang.driver.EDriver;
import erjang.driver.efile.ClassPathResource;
import erjang.driver.efile.EFile;

/**
 * This will eventually be the main entrypoint for an OTP node.
 * Loads preloaded erlang modules, and invokes otp_ring0:start/2
 * 
 */
public class OTPMain {

	public static String[] MODULES = new String[] { "erl_prim_loader",
			"erlang", "init", "otp_ring0", "prim_file", "prim_inet",
			"prim_zip", "zlib" };

	public static EDriver[] DRIVERS = new EDriver[] {
	    new erjang.driver.efile.Driver(),
	    new erjang.driver.ram_file.Driver(),
	    new erjang.driver.tcp_inet.Driver(),
	    new erjang.driver.inet_gethost.Driver(),
	    new erjang.driver.zlib.Driver(),
	    new erjang.driver.js.EJSDriver()
	};

    public static void load_modules_and_drivers(List<String> modules, List<EDriver> drivers) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {

    	if (modules != null) {
	    	String erl_bootstrap_ebindir = ERT.runtime_info.erl_bootstrap_ebindir;
	    	if (ClassPathResource.isResource(erl_bootstrap_ebindir)) {
	
	    		for (String m : modules) {
		    		String beam_path = erl_bootstrap_ebindir + "/" + m + ".beam";
					EBinary bin = ClassPathResource.read_file(beam_path);
					if (bin == null) {
						throw new FileNotFoundException(beam_path);
					}
		    		EModuleLoader.load_module(m, bin);
				}
	    		
	    	} else {    	
		    	for (String m : modules) {
		    		EModuleLoader.find_and_load_module(m);
				}
	    	}
    	}
    	
    	if (drivers != null) {
			for (EDriver d : drivers) {
				erjang.driver.Drivers.register(d);
			}
    	}
    }

    public static void start_otp_ring0(ESeq argv) {
        EAtom am_otp_ring0 = EAtom.intern("otp_ring0");
		EAtom am_start = EAtom.intern("start");
		ESeq env = ERT.NIL;

		// launch first (initial) process, which starts OTP
		EProc proc = new EProc(null, am_otp_ring0, am_start, ERT.NIL.cons(argv).cons(env));

		ERT.run(proc);
		
		// wait for this process to terminate
		proc.joinb();
		
		// shutdown schedulers, after the first (initial) process has stopped
		ERT.shutdownSchedulers();
		// shutdown timer task
		ETimerTask.shutdown();
    }

    protected static ESeq process_args(String[] args) {
        ESeq argv = ERT.NIL;
        for (int i = args.length-1; i >= 0; i--) {
            argv = argv.cons(EBinary.fromString(args[i]));
            
            //special handling for -noshell / -noinput:
            //in this case we suppress the Progress wheel since it might break the output
            if (args[i].equals("-noinput") || args[i].equals("-noshell")) {
                System.setProperty("erjang.progress.suppress", "true");
            }
        }

        return argv;
    }


    static void maybe_print_banner() {
        RuntimeInfo info = ERT.runtime_info;
        if (info != null && !Boolean.getBoolean("erjang.progress.suppress")) {
            System.out.println("** Erjang " + info.otp_version + " ** "
                               + " [root:" + info.erl_rootdir + "]"
                               + " [erts:" + info.erts_version + "]"
                               + " [smp S:" + ERT.threadPoolSize() + " A:"+ ERT.asyncThreadPoolSize()+"]"
                               + " [java:" + System.getProperty("java.version") + "]"
                               + (info.unicodeDriverInterface ? " [unicode]" : "")
                               );
        }
    }

    /**
     * {@link ERT#setRuntimeInfo(RuntimeInfo)} should have been called, before calling this method
     * @param args
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IOException
     */
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
            ESeq argv = process_args(args);
            maybe_print_banner();

	    Handler fh = new FileHandler("erjang.log");
	    Logger.getLogger("").addHandler(fh);
	   // Logger.getLogger("erjang").setLevel(Level.FINE);
	    // Logger.getLogger("kilim.Task").setLevel(Level.FINEST);

		load_modules_and_drivers(Arrays.asList(MODULES), Arrays.asList(DRIVERS));
		load_modules_and_drivers(null, extra_drivers);
        start_otp_ring0(argv);
		
        // ERT.getOutputStream().println("done.");
	}

	static List<EDriver> extra_drivers = new ArrayList<EDriver>();
	public static void add_driver(EDriver driver) {
		extra_drivers.add(driver);
	}
}
