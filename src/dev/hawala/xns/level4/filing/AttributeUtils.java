/*
Copyright (c) 2019, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns.level4.filing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level4.filing.FilingCommon.AccessList;
import dev.hawala.xns.level4.filing.FilingCommon.AccessType;
import dev.hawala.xns.level4.filing.FilingCommon.ArgumentProblem;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeSequence;
import dev.hawala.xns.level4.filing.FilingCommon.FilterAttribute;
import dev.hawala.xns.level4.filing.FilingCommon.FilterRecord;
import dev.hawala.xns.level4.filing.FilingCommon.FilterSequenceRecord;
import dev.hawala.xns.level4.filing.FilingCommon.FilterType;
import dev.hawala.xns.level4.filing.FilingCommon.Interpretation;
import dev.hawala.xns.level4.filing.FilingCommon.Ordering;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeType;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeTypeErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.UndefinedErrorRecord;
import dev.hawala.xns.level4.filing.fs.AccessEntry;
import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.FsConstants;
import dev.hawala.xns.level4.filing.fs.PathElement;
import dev.hawala.xns.level4.filing.fs.UninterpretedAttribute;
import dev.hawala.xns.level4.filing.fs.iValueFilter;
import dev.hawala.xns.level4.filing.fs.iValueGetter;
import dev.hawala.xns.level4.filing.fs.iValueSetter;

/**
 * Utilities for transferring attribute values between the the Courier representation as
 * defined in the Filing protocol and the corresponding {@code FileEntry} fields.
 * <p>
 * This class provides methods resp. lists providing {@code iValueGetter} ({@code FileEntry} to Courier)
 * and {@code iValueSetter} (Courier to {@code FileEntry}) for all kinds of attribute-types defined by
 * the Filing protocol.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class AttributeUtils {
	
	/*
	 * FileEntry => Courier
	 */
	
	private static void file2courier_accessList(AttributeSequence s, long atCode, boolean isDefaulted, List<AccessEntry> accessEntries, boolean forFiling4) {
		AccessList acl = forFiling4 ? AccessList.make4() : AccessList.make5or6();
		acl.defaulted.set(isDefaulted);
		for (AccessEntry a : accessEntries) {
			FilingCommon.AccessEntry fa = acl.entries.add();
			fa.key.from(a.key);
			if (a.access == FsConstants.fullAccess) {
				//fa.access.add().set(AccessType.fullAccess);
				fa.access.add(AccessType.readAccess);
				fa.access.add(AccessType.writeAccess);
				fa.access.add(AccessType.ownerAccess);
				fa.access.add(AccessType.addAccess);
				fa.access.add(AccessType.removeAccess);
			} else {
				if ((a.access & FsConstants.readAccess) != 0) { fa.access.add(AccessType.readAccess); }
				if ((a.access & FsConstants.writeAccess) != 0) { fa.access.add(AccessType.writeAccess); }
				if ((a.access & FsConstants.ownerAccess) != 0) { fa.access.add(AccessType.ownerAccess); }
				if ((a.access & FsConstants.addAccess) != 0) { fa.access.add(AccessType.addAccess); } 
				if ((a.access & FsConstants.removeAccess) != 0) { fa.access.add(AccessType.removeAccess); }
			}
		}
		Attribute attr = s.value.add();
		try {
			attr.encodeData(acl);
		} catch (Exception e) {
			new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_ENCODE_ERROR).raise();
		}
		attr.type.set(atCode);
	}
	
	private static void file2courier_ordering(AttributeSequence s, long key, boolean ascending, int interpretation) {
		Ordering ord = Ordering.make();
		ord.key.set(key);
		ord.ascending.set(ascending);
		switch(interpretation) {
		case FsConstants.intrBoolean: ord.interpretation.set(Interpretation.bool); break;
		case FsConstants.intrCardinal: ord.interpretation.set(Interpretation.cardinal); break;
		case FsConstants.intrLongCardinal: ord.interpretation.set(Interpretation.longCardinal); break;
		case FsConstants.intrInteger: ord.interpretation.set(Interpretation.integer); break;
		case FsConstants.intrLongInteger: ord.interpretation.set(Interpretation.longInteger); break;
		case FsConstants.intrTime: ord.interpretation.set(Interpretation.time); break;
		case FsConstants.intrString: ord.interpretation.set(Interpretation.string); break;
		default: ord.interpretation.set(Interpretation.interpretationNone); break;
		}
		Attribute attr = s.value.add();
		try {
			attr.encodeData(ord);
		} catch (Exception e) {
			new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_ENCODE_ERROR).raise();
		}
		attr.type.set(FilingCommon.atOrdering);
	}
	
	public static void file2courier_uninterpreted(AttributeSequence s, long atCode, FileEntry fe) {
		UninterpretedAttribute a = fe.getUninterpretedAttribute(atCode);
		Attribute attr = s.value.add();
		attr.type.set(atCode);
		if (a == null) { return; }
		for (int i = 0; i < a.size(); i++) {
			attr.value.add().set(a.get(i));
		}
	}
	
	private static void file2courier_position(AttributeSequence s, FileEntry fe) {
		long position = fe.getPosition();
		Attribute attr = s.value.add();
		attr.setAsPosition(position);
	}
	
	private static void file2courier_unsupported(AttributeSequence s, long atCode) {
		Attribute attr = s.value.add();
		attr.value.clear();
		attr.type.set(atCode);
	}
	
	// this list is setup in a static block at module end
	public static final List<iValueGetter<AttributeSequence>> file2courier_interpreted5or6;
	public static final List<iValueGetter<AttributeSequence>> file2courier_interpreted4;
	
	public static void file2courier_allAttributes5or6(AttributeSequence s, FileEntry fe) {
		inner_file2courier_allAttributes(s, fe, file2courier_interpreted5or6);
	}
	
	public static void file2courier_allAttributes4(AttributeSequence s, FileEntry fe) {
		inner_file2courier_allAttributes(s, fe, file2courier_interpreted4);
	}
	
	private static void inner_file2courier_allAttributes(AttributeSequence s, FileEntry fe, List<iValueGetter<AttributeSequence>> file2courier4interpreted) {
		for (int i = 0; i < file2courier4interpreted.size(); i++) {
			innerFillAttribute(s, i, fe, file2courier4interpreted);
		}
		for (UninterpretedAttribute a: fe.getUninterpretedAttributes()) {
			file2courier_uninterpreted(s, a.getType(), fe);
		}
	}
	
	public static void fillAttribute(AttributeSequence s, int attrId, FileEntry fe, int filingVersion) {
		innerFillAttribute(s, attrId, fe, (filingVersion < 5) ? file2courier_interpreted4 : file2courier_interpreted5or6);
	}
	
	public static void fillAttribute4(AttributeSequence s, int attrId, FileEntry fe) {
		innerFillAttribute(s, attrId, fe, file2courier_interpreted4);
	}
	
	private static void innerFillAttribute(AttributeSequence s, int attrId, FileEntry fe, List<iValueGetter<AttributeSequence>> file2courier4interpreted) {
		// transmit attributes (intentionally) without value as null-attribute-value
		if ((attrId == FilingCommon.atReadBy && FsConstants.noUser.equals(fe.getReadBy()))
		||  (attrId == FilingCommon.atCreatedBy && FsConstants.noUser.equals(fe.getCreatedBy()))
		||  (attrId == FilingCommon.atFiledBy && FsConstants.noUser.equals(fe.getCreatedBy()))
		||  (attrId == FilingCommon.atModifiedBy && FsConstants.noUser.equals(fe.getModifiedBy()))
		||  (attrId == FilingCommon.atReadOn && fe.getReadOn() < 0)
		||  (attrId == FilingCommon.atCreatedOn && fe.getCreatedOn() < 0)
		||  (attrId == FilingCommon.atFiledOn && fe.getReadOn() < 0)
		||  (attrId == FilingCommon.atModifiedOn && fe.getModifiedOn() < 0)) {
			s.value.add().setAsNullValue(attrId);
			return;
		}
		// put attribute value
		file2courier4interpreted.get(attrId).access(s, fe);
	}
	
	/*
	 * Courier => FileEntry
	 */
	
	public static iValueSetter courier2file(Attribute a) {
		int type = (int)(a.type.get() & 0xFFFF);
		switch(type) {
		case FilingCommon.atChecksum:
			return fe -> fe.setChecksum(a.getAsCardinal());
		case FilingCommon.atChildrenUniquelyNamed:
			return fe -> fe.setChildrenUniquelyNamed(a.getAsBoolean());
		case FilingCommon.atCreatedBy:
			return fe -> fe.setCreatedBy(a.getAsChsName());
		case FilingCommon.atCreatedOn:
			return fe -> fe.setCreatedOn(a.getAsTime());
		case FilingCommon.atFileID:
		case FilingCommon.atIsDirectory:
		case FilingCommon.atIstemporary:
		case FilingCommon.atModifiedBy:
		case FilingCommon.atModifiedOn:
			return fe -> {}; // new AttributeValueErrorRecord(ArgumentProblem.illegal, a.type.get()).raise();
		case FilingCommon.atName:
			return fe -> fe.setName(a.getAsString());
		case FilingCommon.atNumberOfChildren:
			return fe -> {}; // new AttributeValueErrorRecord(ArgumentProblem.illegal, a.type.get()).raise();
		case FilingCommon.atOrdering:
			return fe -> courier2file_ordering(a, fe);
		case FilingCommon.atParentID:
		case FilingCommon.atPosition:
		case FilingCommon.atReadBy:
		case FilingCommon.atReadOn:
		case FilingCommon.atDataSize:
			return fe -> {};
		case FilingCommon.atType:
			return fe -> fe.setType(a.getAsLongCardinal());
		case FilingCommon.atVersion:
			return fe -> {};
		case FilingCommon.atAccessList:
			return fe -> courier2file_accessList(a, fe, false);
		case FilingCommon.atDefaultAccessList:
			return fe -> courier2file_accessList(a, fe, true);
		case FilingCommon.atPathname:
			return fe -> {}; // filing protocol says it is set on Create, but makes no sense!
		case FilingCommon.atService:    // 22, according to: Filing 8.0 Programmer's Manual (pp. 6-3 etc.)
		case FilingCommon.atBackedUpOn: // 23, according to: Filing 8.0 Programmer's Manual (pp. 6-3 etc.)
		case FilingCommon.atFiledBy:    // 24, according to: Filing 8.0 Programmer's Manual (pp. 6-3 etc.)
		case FilingCommon.atFiledOn:    // 25, according to: Filing 8.0 Programmer's Manual (pp. 6-3 etc.)
			return fe -> {}; // the 4 above attributes are ignored for use-case courier -> file
		case FilingCommon.atStoredSize:
		case FilingCommon.atSubtreeSize:
			return fe -> {};  // ignoring these elegantly circumvents the differing interpretations in Filing4 vs. Filing5/6
		case FilingCommon.atSubtreeSizeLimit:
			return fe -> fe.setSubtreeSizeLimit(a.getAsLongCardinal());
			
		default:
			return fe -> {
				UninterpretedAttribute ua = new UninterpretedAttribute(a.type.get());
				for (int i = 0; i < a.value.size(); i++) {
					ua.add(a.value.get(i).get());
				}
				fe.getUninterpretedAttributes().add(ua);
			};
		}
	}
	
	private static void courier2file_ordering(Attribute a, FileEntry fe) {
		Ordering ord;
		try {
			ord = a.decodeData(Ordering::make);
		} catch (Exception e) {
			// ignored, use defaults
			ord = Ordering.make();
		}
		final int interpretation;
		switch(ord.interpretation.get()) {
		case bool: interpretation = FsConstants.intrBoolean; break;
		case cardinal: interpretation = FsConstants.intrCardinal; break;
		case longCardinal: interpretation = FsConstants.intrLongCardinal; break;
		case time: interpretation = FsConstants.intrTime; break;
		case integer: interpretation = FsConstants.intrInteger; break;
		case longInteger: interpretation = FsConstants.intrLongInteger; break;
		case string: interpretation = FsConstants.intrString; break;
		default: interpretation = FsConstants.intrNone; break;
		}
		
		fe.setOrderingKey(ord.key.get());
		fe.setOrderingAscending(ord.ascending.get());
		fe.setOrderingInterpretation(interpretation);
	}
	
	private static void courier2file_accessList(Attribute a, FileEntry fe, boolean isDefaultAcl) {
		AccessList acl = null;
		try {
			acl = a.decodeData(AccessList::make5or6); // try as post-version 4 AccessLIst
		} catch (Exception e) {
			// does not seem to be version 5 or 6, so check for version 4
		}
		if (acl == null) {
			try {
				acl = a.decodeData(AccessList::make4); // try as version 4 AccessList
			} catch (Exception e) {
				// none of the supported versions was ok, so use defaults
				acl = AccessList.make5or6();
			}
		}
		
		if (isDefaultAcl) {
			fe.setDefaultAccessListDefaulted(acl.defaulted.get());
		} else {
			fe.setAccessListDefaulted(acl.defaulted.get());
		}
		
		List<AccessEntry> aes = (isDefaultAcl) ? fe.getDefaultAccessList() : fe.getAccessList();
		aes.clear();
		for (int i = 0; i < acl.entries.size(); i++) {
			FilingCommon.AccessEntry ac = acl.entries.get(i);
			String key = ac.key.getString();
			int access = 0;
			if (ac.access.has(AccessType.fullAccess))   { access |= FsConstants.fullAccess; }
			if (ac.access.has(AccessType.readAccess))   { access |= FsConstants.readAccess; }
			if (ac.access.has(AccessType.writeAccess))  { access |= FsConstants.writeAccess; }
			if (ac.access.has(AccessType.ownerAccess))  { access |= FsConstants.ownerAccess; }
			if (ac.access.has(AccessType.addAccess))    { access |= FsConstants.addAccess; }
			if (ac.access.has(AccessType.removeAccess)) { access |= FsConstants.removeAccess; }
			AccessEntry ae = new AccessEntry(key, access);
			aes.add(ae);
		};
	}
	
	static {
		// build the file=>courier attribute mapping array for filing versions 5 and 6
		file2courier_interpreted5or6 = Arrays.asList(
				// atChecksum = 0
				(s,fe) -> s.value.add().setAsCardinal(FilingCommon.atChecksum, fe.getChecksum()),
				
				// atChildrenUniquelyNamed = 1;
				(s,fe) -> s.value.add().setAsBoolean(FilingCommon.atChildrenUniquelyNamed, fe.isChildrenUniquelyNamed()),
				
				// atCreatedBy = 2
				(s,fe) -> s.value.add().setAsChsName(FilingCommon.atCreatedBy, fe.getCreatedBy()),
				
				// atCreatedOn = 3
				(s,fe) -> s.value.add().setAsTime(FilingCommon.atCreatedOn, fe.getCreatedOn()),
				
				// atFileID = 4
				(s,fe) -> s.value.add().setAsFileID(FilingCommon.atFileID, fe.getFileID()),
				
				// atIsDirectory = 5
				(s,fe) -> s.value.add().setAsBoolean(FilingCommon.atIsDirectory, fe.isDirectory()),
				
				// atIstemporary = 6
				(s,fe) -> s.value.add().setAsBoolean(FilingCommon.atIstemporary, fe.isTemporary()),
				
				// atModifiedBy = 7
				(s,fe) -> s.value.add().setAsChsName(FilingCommon.atModifiedBy, fe.getModifiedBy()),
				
				// atModifiedOn = 8
				(s,fe) -> s.value.add().setAsTime(FilingCommon.atModifiedOn, fe.getModifiedOn()),
				
				// atName = 9
				(s,fe) -> s.value.add().setAsString(FilingCommon.atName, fe.getName()),
				
				// atNumberOfChildren = 10
				(s,fe) -> s.value.add().setAsCardinal(FilingCommon.atNumberOfChildren, fe.getNumberOfChildren()),
				
				// atOrdering = 11 
				(s,fe) -> file2courier_ordering(s, fe.getOrderingKey(), fe.isOrderingAscending(), fe.getOrderingInterpretation()),
				
				// atParentID = 12
				(s,fe) -> {
					long pfId = fe.getParentID();
					if ((pfId == 0 || pfId == FsConstants.rootFileID) && fe.getType() == FilingCommon.tVPDrawer) {
						// when StarOS makes a reference, it asks 2 levels of parents from the item to
						// make the reference; opening the reference later will do a find() for the name of the
						// referenced item from the parent. If either of the parentIds is nullFileID, the reference will
						// start searching at the filesystem root (instead of the direct parent of the referenced item)
						// so find(9 fails for items in a file drawer (bug in StarOS 5.0?).
						// so for file drawers we deliver a dummy parent, which is also root, but with a different
						// (additional) fileId, so a filedrawers parent is not nullFileId, although it is root...
						pfId = FilingCommon.DRAWER_PARENT_ROOTDIR_FILEID;
					}
					s.value.add().setAsFileID(FilingCommon.atParentID, pfId);
				},
				
				// atPosition = 13
				(s,fe) -> file2courier_position(s, fe),
				
				// atReadBy = 14
				(s,fe) -> s.value.add().setAsChsName(FilingCommon.atReadBy, fe.getReadBy()),
				
				// atReadOn = 15
				(s,fe) -> s.value.add().setAsTime(FilingCommon.atReadOn, fe.getReadOn()),
				
				// atDataSize = 16
				(s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atDataSize, fe.getDataSize()),
				
				// atType = 17
				(s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atType, fe.getType()),
				
				// atVersion = 18
				(s,fe) -> s.value.add().setAsCardinal(FilingCommon.atVersion, fe.getVersion()),
				
				// atAccessList = 19;
				(s,fe) -> file2courier_accessList(s, FilingCommon.atAccessList, fe.isAccessListDefaulted(), fe.getAccessList(), false),
				
				// atDefaultAccessList = 20
				(s,fe) -> file2courier_accessList(s, FilingCommon.atDefaultAccessList, fe.isDefaultAccessListDefaulted(), fe.getDefaultAccessList(), false),
				
				// atPathname = 21
				(s,fe) -> s.value.add().setAsString(FilingCommon.atPathname, fe.getPathname()),
				
				// atService = 22
				(s,fe) -> file2courier_unsupported(s, 22),
				
				// atBackedUpOn = 23
				(s,fe) -> s.value.add().setAsTime(FilingCommon.atBackedUpOn, FilingCommon.BackedUp_Never),
				
				// atFileBy = 24
				(s,fe) -> s.value.add().setAsChsName(FilingCommon.atFiledBy, fe.getCreatedBy()), // filed{By,On} and created{By,On} are treated same
				
				// atFiledOn = 25
				(s,fe) -> s.value.add().setAsTime(FilingCommon.atFiledOn, fe.getCreatedOn()), // filed{By,On} and created{By,On} are treated same
				
				// atStoredSize = 26
				// (s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atStoredSize, mapSize(fe, fe.getStoredSize())),
				(s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atStoredSize, fe.getStoredSize()),
				
				// atSubtreeSize = 27
				// (s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atSubtreeSize, mapSize(fe, fe.getSubtreeSize())),
				(s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atSubtreeSize, fe.getSubtreeSize()),
				
				// atSubtreeSizeLimit = 28
				(s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atSubtreeSizeLimit, fe.getSubtreeSizeLimit())
				
				);

		// derive the file=>courier attribute mapping array for filing version 4 by copying the version5/6 list and replacing version specific mappings
		file2courier_interpreted4 = new ArrayList<>(file2courier_interpreted5or6);
		// access-lists: access value has a different representation in Filing4 (bitmap in a word instead of enum-sequence)
		file2courier_interpreted4.set(19, (s,fe) -> file2courier_accessList(s, FilingCommon.atAccessList, fe.isAccessListDefaulted(), fe.getAccessList(), true));
		file2courier_interpreted4.set(20, (s,fe) -> file2courier_accessList(s, FilingCommon.atDefaultAccessList, fe.isDefaultAccessListDefaulted(), fe.getDefaultAccessList(), true));
		// stored&subtree sizes are given in pages in Filing4 (instead of bytes)
		file2courier_interpreted4.set(26, (s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atStoredSize, (fe.getStoredSize() + 511) / 512) );
		file2courier_interpreted4.set(27, (s,fe) -> s.value.add().setAsLongCardinal(FilingCommon.atSubtreeSize, (fe.getSubtreeSize() + 511) / 512) );
	}
	
	public static iValueFilter buildPredicate(CHOICE<FilterType> filter, FileEntry baseDir) {
		// handle structural filter types first
		switch(filter.getChoice()) {
		case all:
				return fe -> true;
		case none:
				return fe -> false;
		case not: {
				FilterRecord tmpFilter = (FilterRecord)filter.getContent();
				iValueFilter tmpPredicate = buildPredicate(tmpFilter.value, baseDir);
				return fe -> !tmpPredicate.isElligible(fe);
			}
		case or:
		case and: {
				FilterSequenceRecord tmpFilters = (FilterSequenceRecord)filter.getContent();
				if (tmpFilters.seq.size() == 0) {
					return fe -> false;
				}
				iValueFilter[] tmpPredicates = new iValueFilter[tmpFilters.seq.size()];
				for (int i = 0; i < tmpPredicates.length; i++) {
					tmpPredicates[i] = buildPredicate(tmpFilters.seq.get(i), baseDir);
				}
				if (filter.getChoice() == FilterType.or) {
					return fe -> {
						for (iValueFilter p : tmpPredicates) {
							if (p.isElligible(fe)) { return true; }
						}
						return false;
					};
				} else {
					return fe -> {
						for (iValueFilter p : tmpPredicates) {
							if (!p.isElligible(fe)) { return false; }
						}
						return true;
					};
				}
			}
		default:
		}
		
		// value based filter, special case pattern-matching
		if (filter.getChoice() == FilterType.matches) {
			Attribute attr = (Attribute)filter.getContent();
			if (attr.type.get() == FilingCommon.atName) {
				String pattern = convertPattern(attr.getAsString(), PathElement.ANY_VERSION).pattern;
				return fe -> fe.getLcName().matches(pattern);
			} else if (attr.type.get() == FilingCommon.atPathname) { // build a special pathname pattern matcher				
				// get the pathName segments
				String pathPattern = attr.getAsString();
				
				System.out.printf("+++ buildPredicate for 'matches' type 'atPathname' :: »%s«\n", pathPattern);
				List<PathElement> pathElements = PathElement.parse(pathPattern, true);
				if (pathElements.isEmpty()) {
					new ScopeTypeErrorRecord(ArgumentProblem.unreasonable, ScopeType.filter).raise();
				}
				
				// get the patterns to match each segment, ordered from the end (the file to find) towards the base-directory of the search 
				List<PatternDef> patternsLeafToBaseDir = new ArrayList<>();
				for (int idx = pathElements.size() - 1; idx >= 0; idx--) {
					PathElement elem = pathElements.get(idx);
					patternsLeafToBaseDir.add(convertPattern(elem.getName(), elem.getVersion()));
				}
				
				System.out.printf("+++\n");
				System.out.printf("+++ search base-dir: »%s«\n", (baseDir == null) ? "(volume-root)" : baseDir.getName());
				System.out.printf("+++ path-segment matcher:\n");
				for (PatternDef pat : patternsLeafToBaseDir) {
					System.out.printf("+++   %s\n", pat.toString());
				}
				System.out.printf("+++\n");
				
				// produce the more complex matcher over path elements
				return new PathnameMatcher(patternsLeafToBaseDir, baseDir);
			} else {
				new ScopeTypeErrorRecord(ArgumentProblem.unreasonable, ScopeType.filter).raise();
			}
		}
		
		// value comparison based filter
		final Predicate<Integer> evaluator;
		switch(filter.getChoice()) {
		case less:
			evaluator = i -> (i < 0);
			break;
		case lessOrEqual:
			evaluator = i -> (i <= 0);
			break;
		case equal:
			evaluator = i -> (i == 0);
			break;
		case notEqual:
			evaluator = i -> (i != 0);
			break;
		case greaterOrEqual:
			evaluator = i -> (i >= 0);
			break;
		case greater:
			evaluator = i -> (i > 0);
			break;
		default:
			evaluator = i -> false; // none of the comparators...	
		}
		Attribute attr = ((FilterAttribute)filter.getContent()).attribute;
		int type = (int)(attr.type.get() & 0xFFFF);
		switch(type) {
		case FilingCommon.atChecksum: {
			int val = attr.getAsCardinal();
			return fe -> compare(val, fe.getChecksum(), evaluator);
			}
		case FilingCommon.atChildrenUniquelyNamed: {
			boolean val = attr.getAsBoolean();
			return fe -> compare(val, fe.isChildrenUniquelyNamed(), evaluator);
			}
		case FilingCommon.atCreatedBy: {
			String val = attr.getAsString().toLowerCase();
			return fe -> compare(val, fe.getCreatedBy().toLowerCase(), evaluator);
			}
		case FilingCommon.atCreatedOn: {
			long val = attr.getAsTime();
			return fe -> compare(val, fe.getCreatedOn(), evaluator);
			}
		case FilingCommon.atFileID: {
			long val = attr.getAsFileID();
			return fe -> compare(val, fe.getFileID(), evaluator);
			}
		case FilingCommon.atIsDirectory: {
			boolean val = attr.getAsBoolean();
			return fe -> compare(val, fe.isDirectory(), evaluator);
			}
		case FilingCommon.atModifiedBy: {
			String val = attr.getAsString().toLowerCase();
			return fe -> compare(val, fe.getModifiedBy().toLowerCase(), evaluator);
			}
		case FilingCommon.atModifiedOn: {
			long val = attr.getAsTime();
			return fe -> compare(val, fe.getModifiedOn(), evaluator);
			}
		case FilingCommon.atName: {
			String val = attr.getAsString().toLowerCase();
			return fe -> compare(val, fe.getName().toLowerCase(), evaluator);
			}
		case FilingCommon.atNumberOfChildren: {
			int val = attr.getAsCardinal();
			return fe -> compare(val, fe.getNumberOfChildren(), evaluator);
			}
		case FilingCommon.atParentID: {
			long val = attr.getAsFileID();
			return fe -> compare(val, fe.getParentID(), evaluator);
			}
		case FilingCommon.atPosition: {
			long val = attr.getAsPosition();
			return fe -> compare(val, fe.getPosition(), evaluator);
			}
		case FilingCommon.atReadBy: {
			String val = attr.getAsString().toLowerCase();
			return fe -> compare(val, fe.getReadBy().toLowerCase(), evaluator);
			}
		case FilingCommon.atReadOn: {
			long val = attr.getAsTime();
			return fe -> compare(val, fe.getReadOn(), evaluator);
			}
		case FilingCommon.atDataSize: {
			long val = attr.getAsLongCardinal();
			return fe -> compare(val, fe.getDataSize(), evaluator);
			}
		case FilingCommon.atType: {
			long val = attr.getAsLongCardinal();
			return fe -> compare(val, fe.getType(), evaluator);
			}
		case FilingCommon.atVersion: {
			int val = attr.getAsCardinal();

			// check if the file is the first available version for the name?
			if (val == 0) { return fe -> fe.isLowestVersion(); }

			// check if the file is the last available version for the name?
			if (val == 0xFFFF) { return fe -> fe.isHighestVersion(); }
			
			// anything else: check the file for the specified version
			return fe -> compare(val, fe.getVersion(), evaluator);
			}
		case FilingCommon.atPathname: {
			String val = attr.getAsString().toLowerCase();
			return fe -> compare(val, fe.getPathname().toLowerCase(), evaluator);
			}
		case FilingCommon.atStoredSize: {
			long val = attr.getAsLongCardinal();
			return fe -> compare(val, fe.getStoredSize(), evaluator);
			}
		case FilingCommon.atSubtreeSize: {
			long val = attr.getAsLongCardinal();
			return fe -> compare(val, fe.getSubtreeSize(), evaluator);
			}
		case FilingCommon.atSubtreeSizeLimit: {
			long val = attr.getAsLongCardinal();
			return fe -> compare(val, fe.getSubtreeSizeLimit(), evaluator);
			}
		default:
			break;
		}
		
		
		// unknown/invalid filter type...
		// or unsupported (ordering, accessLists, uninterpreted) or meaningless (e.g. isTemporary) attribute
		return fe -> false;
	}
	
	private static <T extends Comparable<T>> boolean compare(T constant, T value, Predicate<Integer> evaluator) {
		return evaluator.test(value.compareTo(constant));
	}
	
	/**
	 * pathName search pattern filter
	 * 
	 * the search strategy is to match the requested path patterns from the leaf (candidate file)
	 * to the base directory of the search, so the singular patterns derived from the 'atPathname'-filter
	 * are expected in reversed order ("from right to left") when constructing the 'PathnameMatcher' and
	 * they will be applied in this order, matching the patterns from the candidate file up the path until
	 * the search base directory is reached;
	 * this strategy of going backwards in the 'atPathname'-pattern allows to apply the '**'-wildcard
	 * in a path-element ("match multiple components", meaning that this path-segment-pattern can match in
	 * this directory or its sub-directories) in the 'iValueFilter'-context, where we do not actively
	 * search for items matching the pattern, but have to verify if a candidate satisfies the pattern,
	 * where the presence the 0..n intermediate directories for a given path-pattern can also be checked
	 * when testing the parent(s) for the parent-path-pattern.   
	 * 
	 */
	private static class PathnameMatcher implements iValueFilter {
		
		private final List<PatternDef> patternsLeafToBaseDir;
		private final FileEntry baseDir;
		
		private PathnameMatcher(List<PatternDef> patternsLeafToBaseDir, FileEntry baseDir) {
			this.patternsLeafToBaseDir = patternsLeafToBaseDir;
			this.baseDir = baseDir;
		}
		
		private boolean matches(FileEntry currFe, PatternDef patternDef) {
			if (currFe == null || !currFe.getLcName().matches(patternDef.pattern)) {
				return false;
			}
			if (patternDef.version == PathElement.LOWEST_VERSION) {
				return currFe.isLowestVersion();
			} else if (patternDef.version == PathElement.HIGHEST_VERSION) {
				return currFe.isHighestVersion();
			} else if (patternDef.version != PathElement.ANY_VERSION) {
				return (currFe.getVersion() == patternDef.version);
			}
			return true;
		}

		@Override
		public boolean isElligible(FileEntry fe) {
			// start at the end of both the candidate file and the path-patterns
			FileEntry currFe = fe;
			PatternDef currPattern = this.patternsLeafToBaseDir.get(0);
			
			// check the candidate file itself (this is the path leaf and *must* match the first pattern)
			boolean currFeMatches = this.matches(currFe, currPattern);
			if (!currFeMatches) { return false; }
			
			// check the up-tree of the candidate file
			boolean allowIntermediates = currPattern.matchMultipleLevels;
			currFe = currFe.getParent();
			for (int idx = 1; idx < this.patternsLeafToBaseDir.size(); idx++) {
				// the new (next up-level) search path pattern
				currPattern = this.patternsLeafToBaseDir.get(idx);
				
				// check if the file matches the current pattern
				// or
				// if (and only if) the previous pattern allowed to find it down-tree
				// then iterate upwards until an ancestor matches
				currFeMatches = this.matches(currFe, currPattern);
				while (allowIntermediates && currFe != this.baseDir && !currFeMatches) {
					currFe = currFe.getParent();
					currFeMatches = this.matches(currFe, currPattern);
				}
				if (currFe == this.baseDir) {
					// we arrived at the start directory for the search,
					// but this path-pattern it to be applied to the descendants
					// of the start directory, so...
					return false;
				}
				if (!currFeMatches) {
					return false;
				}
				
				// prepare for next search path pattern/element
				currFe = currFe.getParent();
				allowIntermediates = currPattern.matchMultipleLevels; // check *next* pattern on ancestors if *this* pattern had "match multiple components"
			}
			
			// if we are here, all search pattern elements were satisfied by the up-tree path of 'fe', 
			// so finally check if we are "close enough" to the 'baseDir'
			return allowIntermediates        // if "match multiple components": 'baseDir' is always an ancestor of 'currFe'
				|| (currFe == this.baseDir); // no "match multiple components": 'baseDir' must be the direct parent of the last match
		}
		
	}
	
	/*
	 * search patterns (Filing-wildcards => Java-Regex)
	 */
	
	private static class PatternDef {
		private final String pattern;
		private final int version;
		private final boolean matchMultipleLevels;
		
		private PatternDef(String pattern, int version, boolean matchMultipleLevels) {
			this.pattern = pattern;
			this.version = version;
			this.matchMultipleLevels = matchMultipleLevels;
		}

		@Override
		public String toString() {
			return String.format("PatternDef [segment-regex = »%s« , version = %s , multi-level-match = %s",
					this.pattern,
					(this.version == PathElement.LOWEST_VERSION)
					? "lowest"
					: (this.version == PathElement.HIGHEST_VERSION)
					? "highest"
					: (this.version == PathElement.ANY_VERSION)
					? "any"
					: Integer.toString(this.version),
					this.matchMultipleLevels);
		}
		
	}
	
	private static PatternDef convertPattern(String xnsPattern, int version) {
		StringBuilder sb = new StringBuilder();
		boolean matchMultipleLevels = false;
		
		char[] patternChars = xnsPattern.toCharArray();
		boolean lastWasEscape = false;
		boolean lastWasStar = false;
		
		for (char c : patternChars) {
			if (lastWasEscape) {
				appendPlainChar(sb, c);
				lastWasEscape = false;
			} else if (c == '\'') {
				lastWasEscape = true;
				lastWasStar = false;
			} else if (c == '#') {
				sb.append(".{1}");
				lastWasStar = false;
			} else if (c == '*') {
				if (lastWasStar) {
					matchMultipleLevels = true;
					// lastWasStar = false; -- don't reset in case of more than 2 stars => collapse them to a single ".*"
				} else {
					sb.append(".*");
					lastWasStar = true;
				}
			} else {
				appendPlainChar(sb, c);
				lastWasStar = false;
			}
		}
		
		return new PatternDef(sb.toString().toLowerCase(), version, matchMultipleLevels);
	}
	
	private static void appendPlainChar(StringBuilder sb, char c) {
		if (c == '.' || c == '+' || c == '*' || c == '?' || c == '(' || c == '[' || c == '{' || c == '|' || c == '^' || c == '\\') {
			sb.append('\\').append(c);
		} else {
			sb.append(Character.toLowerCase(c));
		}
	}
	
}