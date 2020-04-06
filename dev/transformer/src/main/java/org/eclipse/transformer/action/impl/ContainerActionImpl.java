/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package org.eclipse.transformer.action.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.eclipse.transformer.TransformException;
import org.eclipse.transformer.action.Action;
import org.eclipse.transformer.action.ActionType;
import org.eclipse.transformer.action.ContainerAction;
import org.eclipse.transformer.action.ContainerChanges;
import org.eclipse.transformer.util.ByteData;
import org.eclipse.transformer.util.FileUtils;
import org.eclipse.transformer.util.InputStreamData;
import org.slf4j.Logger;

public abstract class ContainerActionImpl extends ActionImpl implements ContainerAction {

	public <A extends ActionImpl> A addUsing(ActionInit<A> init) {
		A action = createUsing(init);
		addAction(action);
		return action;
	}

	public ContainerActionImpl(
		Logger logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);

		this.compositeAction = createUsing( CompositeActionImpl::new );
	}

	//

	private final CompositeActionImpl compositeAction;

	@Override
	public CompositeActionImpl getAction() {
		return compositeAction;
	}

	public void addAction(ActionImpl action) {
		getAction().addAction(action);
	}

	@Override
	public List<ActionImpl> getActions() {
		return getAction().getActions();
	}

	@Override
	public String getAcceptExtension() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ActionImpl acceptAction(String resourceName) {
		return acceptAction(resourceName, null);
	}

	@Override
	public ActionImpl acceptAction(String resourceName, File resourceFile) {
		return getAction().acceptAction(resourceName, resourceFile);
	}

	//

	@Override
	public abstract String getName();

	@Override
	public abstract ActionType getActionType();

	//

	@Override
	protected ContainerChangesImpl newChanges() {
		return new ContainerChangesImpl();
	}

	@Override
	public ContainerChangesImpl getChanges() {
		return (ContainerChangesImpl) super.getChanges();
	}

	//

	protected void recordUnaccepted(String resourceName) {
		debug("Resource [ {} ]: Not accepted", resourceName);

		getChanges().record();
	}

	protected void recordUnselected(Action action, boolean hasChanges, String resourceName) {
		debug( "Resource [ {} ] Action [ {} ]: Accepted but not selected",
			   resourceName, action.getName() );

		getChanges().record(action, hasChanges);
	}

	protected void recordTransform(Action action, String resourceName) {
		debug( "Resource [ {} ] Action [ {} ]: Changes [ {} ]",
			   resourceName, action.getName(), action.hasChanges() );

		getChanges().record(action);
	}

	// Byte base container conversion is not supported.

	public boolean useStreams() {
		return true;
	}

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength)
		throws TransformException {
		throw new UnsupportedOperationException();
	}

	// Containers default to process input streams as zip archives.

	@Override
	public void apply(
		String inputPath, InputStream inputStream, long inputCount,
		OutputStream outputStream) throws TransformException {

		setResourceNames(inputPath, inputPath);

		// Use Zip streams instead of Jar streams.
		//
		// Jar streams automatically read and consume the manifest, which we don't want.

		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

		try {
			apply(inputPath, zipInputStream, zipOutputStream); // *1 *3
			// throws JakartaTransformException

		} finally {
			try {
				zipOutputStream.finish(); // throws IOException
			} catch ( IOException e ) {
				throw new TransformException("Failed to complete output [ " + inputPath + " ]", e);
			}
		}
	}

	protected void apply(
		String inputPath, ZipInputStream zipInputStream,
		ZipOutputStream zipOutputStream) throws TransformException {

		String prevName = null;
		String inputName = null;

		try {
			byte[] buffer = new byte[FileUtils.BUFFER_ADJUSTMENT];

			ZipEntry inputEntry;
			while ( (inputEntry = zipInputStream.getNextEntry()) != null ) {
				inputName = inputEntry.getName();
				long inputLength = inputEntry.getSize();

				debug( "[ {}.{} ] [ {} ] Size [ {} ]",
					   getClass().getSimpleName(), "apply", inputName, inputLength );

				boolean selected = select(inputName);
				Action acceptedAction = acceptAction(inputName);

				if ( !selected || (acceptedAction == null) ) {
					if ( acceptedAction == null ) {
						recordUnaccepted(inputName);
					} else {
						recordUnselected(acceptedAction, !ContainerChanges.HAS_CHANGES, inputName);
					}

					// TODO: Should more of the entry details be transferred?

					ZipEntry outputEntry = new ZipEntry(inputName);
					zipOutputStream.putNextEntry(outputEntry); // throws IOException
					FileUtils.transfer(zipInputStream, zipOutputStream, buffer); // throws IOException 
					zipOutputStream.closeEntry(); // throws IOException

				} else {
//					long inputCRC = inputEntry.getCrc();
//
//					int inputMethod = inputEntry.getMethod();
//					long inputCompressed = inputEntry.getCompressedSize();
//
//					FileTime inputCreation = inputEntry.getCreationTime();
//					FileTime inputAccess = inputEntry.getLastAccessTime();
//					FileTime inputModified = inputEntry.getLastModifiedTime();
//
//					String className = getClass().getSimpleName();
//					String methodName = "applyZip";
//
//					debug( "[ {}.{} ] [ {} ] Size [ {} ] CRC [ {} ]",
//						   className, methodName, inputName, inputLength, inputCRC);
//					debug( "[ {}.{} ] [ {} ] Compressed size [ {} ] Method [ {} ]",
//						   className, methodName, inputName, inputCompressed, inputMethod);
//					debug( "[ {}.{} ] [ {} ] Created [ {} ] Accessed [ {} ] Modified [ {} ]",
//						   className, methodName, inputName, inputCreation, inputAccess, inputModified);

					// Archive type actions are processed using streams,
					// while non-archive type actions do a full read of the entry
					// data and process the resulting byte array.
					//
					// Ideally, a single pattern would be used for both cases, but
					// but that is not possible:
					//
					// A full read of a nested archive is not possible because the nested
					// archive can be very large.
					//
					// A read of non-archive data must be performed, since non-archive data
					// may change the name associated with the data, and that can only be
					// determined after reading the data.

					if ( acceptedAction.useStreams() ) {
						// TODO: Should more of the entry details be transferred?

						ZipEntry outputEntry = new ZipEntry(inputName);
						zipOutputStream.putNextEntry(outputEntry); // throws IOException

						acceptedAction.apply(inputName, zipInputStream, inputLength, zipOutputStream); // *2
						recordTransform(acceptedAction, inputName);
						zipOutputStream.closeEntry(); // throws IOException

					} else {
						int intInputLength;
						if ( inputLength == -1L ) {
							intInputLength = -1;
						} else {
							intInputLength = FileUtils.verifyArray(0, inputLength);
						}

						InputStreamData outputData =
							acceptedAction.apply(inputName, zipInputStream, intInputLength);
						recordTransform(acceptedAction, inputName);

						// TODO: Should more of the entry details be transferred?

						ZipEntry outputEntry = new ZipEntry( acceptedAction.getChanges().getOutputResourceName() );
						zipOutputStream.putNextEntry(outputEntry); // throws IOException // *4
						FileUtils.transfer(outputData.stream, zipOutputStream, buffer); // throws IOException 
						zipOutputStream.closeEntry(); // throws IOException
					}
				}

				prevName = inputName;
				inputName = null;
			}

		} catch ( IOException e ) {
			String message;
			if ( inputName != null ) { // Actively processing an entry.
				message = "Failure while processing [ " + inputName + " ] from [ " + inputPath + " ]";
			} else if ( prevName != null ) { // Moving to a new entry but not the first entry.
				message = "Failure after processing [ " + prevName + " ] from [ " + inputPath + " ]";
			} else { // Moving to the first entry.
				message = "Failed to process first entry of [ " + inputPath + " ]";
			}
			throw new TransformException(message, e);
		}
	}
	
//	[main] ERROR Transformer - Transform failure: %s
//	org.eclipse.transformer.TransformException: Failure while processing [ com/sun/ts/tests/servlet/api/javax_servlet/singlethreadmodel/STMClientServlet$ThreadClient$TestThread.class ] from [ WEB-INF/lib/servlet_plu_singlethreadmodel.jar ]
//	        at org.eclipse.transformer.action.impl.ContainerActionImpl.apply(ContainerActionImpl.java:289)
//	        at org.eclipse.transformer.action.impl.ContainerActionImpl.apply(ContainerActionImpl.java:164)
//	        at org.eclipse.transformer.action.impl.ContainerActionImpl.apply(ContainerActionImpl.java:251)
//	        at org.eclipse.transformer.action.impl.ContainerActionImpl.apply(ContainerActionImpl.java:164)
//	        at org.eclipse.transformer.action.impl.ActionImpl.apply(ActionImpl.java:491)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:109)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:99)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:99)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:99)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:99)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.transform(DirectoryActionImpl.java:99)
//	        at org.eclipse.transformer.action.impl.DirectoryActionImpl.apply(DirectoryActionImpl.java:73)
//	        at org.eclipse.transformer.Transformer$TransformOptions.transform(Transformer.java:1168)
//	        at org.eclipse.transformer.Transformer.run(Transformer.java:1243)
//	        at com.ibm.ws.jakarta.transformer.JakartaTransformer.main(JakartaTransformer.java:30)
//	Caused by: java.util.zip.ZipException: duplicate entry: com/sun/ts/tests/servlet/api/javax_servlet/singlethreadmodel/STMClientServlet$ThreadClient$TestThread.class
//	        at java.base/java.util.zip.ZipOutputStream.putNextEntry(ZipOutputStream.java:233)
//	        at org.eclipse.transformer.action.impl.ContainerActionImpl.apply(ContainerActionImpl.java:270)
//	        ... 14 more	
}