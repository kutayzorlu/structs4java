/*
 * generated by Xtext 2.10.0
 */
package org.structs4java.generator

import org.structs4java.structs4JavaDsl.ComplexTypeDeclaration
import org.structs4java.structs4JavaDsl.ComplexTypeMember
import org.structs4java.structs4JavaDsl.FloatMember
import org.structs4java.structs4JavaDsl.IntegerMember
import org.structs4java.structs4JavaDsl.Member
import org.structs4java.structs4JavaDsl.Package
import org.structs4java.structs4JavaDsl.StringMember
import org.structs4java.structs4JavaDsl.StructDeclaration

/**
 * Generates code from your model files on save.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#code-generation
 */
class StructGenerator {

	def compile(Package pkg, StructDeclaration struct) '''
		«packageDeclaration(pkg)»
		
		public class «struct.name» {
			
			«fields(struct)»
			
			public «struct.name»() {
			}
			
			«getters(struct)»
			«setters(struct)»
			
			«reader(struct)»
			«writer(struct)»
			
			«IF hasAtLeastOneStringMember(struct)»
				«stringReader()»
			«ENDIF»
		}
	'''

	def reader(StructDeclaration struct) '''
		public static «struct.name» read(java.nio.ByteBuffer buf) throws java.io.IOException {
			«struct.name» obj = new «struct.name»();
			«FOR f : struct.members»
				«alignField(f)»
				«IF isArray(f)»
					«readArray(f)»
				«ELSE»
					«setField("obj", f, readField(f))»
				«ENDIF»
			«ENDFOR»
			return obj;
		}
	'''

	def isArray(Member m) {
		return m.array != null
	}

	def readArray(Member m) {
		if (m.array.dimension > 0) {
			return readStaticArray(m)
		} else {
			return readDynamicArray(m)
		}
	}

	def readStaticArray(Member m) '''
		{
			«native2JavaType(m)» lst = new «native2JavaType(m)»();
		«FOR i : 0 ..< m.array.dimension»
			«alignField(m)»
			lst.add(«readField(m)»);
		«ENDFOR»
			«setField("obj", m, "lst")»
		}
	'''

	def readDynamicArray(Member m) '''
		{
			int arrayLength = this.«getterName(sizeMember(m.eContainer as StructDeclaration, m))»();
			«native2JavaType(m)» lst = new «native2JavaType(m)»();
			for(int i = 0; i < arrayLength; ++i) {
				«alignField(m)»
				lst.add(«readField(m)»);
			}
			«setField("obj", m, "lst")»
		}
	'''

	def sizeMember(StructDeclaration struct, Member m) {
		for (member : struct.members) {
			if (member instanceof IntegerMember) {
				val imem = member as IntegerMember
				if (imem.sizeof.contains(m)) {
					return imem
				}
			}
		}
		return null
	}

	def setField(String variable, Member m, CharSequence expr) '''
		«variable».«setterName(m)»(«expr»);
	'''

	def setterName(Member m) {
		return "set" + attributeName(m).toFirstUpper();
	}

	def getterName(Member m) {
		return "get" + attributeName(m).toFirstUpper();
	}

	def attributeName(Member m) {
		return m.name;
	}

	def readField(Member m) {
		switch (m) {
			ComplexTypeMember: readField(m as ComplexTypeMember)
			IntegerMember: readField(m as IntegerMember)
			FloatMember: readField(m as FloatMember)
			StringMember: readField(m as StringMember)
		}
	}

	def readField(ComplexTypeMember m) {
		val nativeType = nativeTypeName(m)
		val javaType = native2JavaType(nativeType)
		return javaType + ".read(buf)";	
	}

	def readField(IntegerMember m) {
		switch (m.typename) {
			case "int8_t": '''buf.get()'''
			case "uint8_t": '''buf.get() & 0xFF'''
			case "int16_t": '''buf.getShort()'''
			case "uint16_t": '''buf.getShort() & 0xFFFF'''
			case "int32_t": '''buf.getInt()'''
			case "uint32_t": '''buf.getInt() & 0xFFFFFFFF'''
			case "int64_t": '''buf.getLong()'''
			case "uint64_t": '''buf.getLong() & 0xFFFFFFFFFFFFFFFFL'''
			default:
				throw new RuntimeException("Unsupported type: " + m.typename)
		}
	}

	def readField(FloatMember m) {
		switch (m.typename) {
			case "float": '''buf.getFloat()'''
			case "double": '''buf.getDouble()'''
		}
	}

	def readField(StringMember m) '''
		readString(buf, "«encodingOf(m)»", «dimensionOf(m)»)
	'''
	
	def dimensionOf(StringMember m) {
		if(m.array != null) {
			return m.array.dimension
		}
		return 0
	}

	def stringReader() '''
		private static String readString(java.nio.ByteBuffer buf, String encoding, int size) throws java.io.IOException {
			try {
				if(size == 0) {
					int terminatingZeros = "\0".getBytes(encoding).length;
					java.io.ByteArrayOutputStream tmp = new java.io.ByteArrayOutputStream();
					int zerosRead = 0;
					while(zerosRead < terminatingZeros) {
						int b = buf.get();
						tmp.write(b);
						if(b == 0) {
							zerosRead++;
						} else {
							zerosRead = 0;
						}
					}
					return tmp.toString(encoding);
				} else {
					byte[] tmp = new byte[size];
					buf.get(tmp);
					return new String(tmp, encoding);
				}
			} catch(java.io.UnsupportedEncodingException e) {
				throw new java.io.IOException(e);
			}
		}
	'''

	def writer(StructDeclaration struct) '''
		public void write(java.nio.ByteBuffer buf) throws java.io.IOException {
			«FOR f : struct.members»
				«alignField(f)»
				«IF isArray(f)»
					«writeArray(f)»
				«ELSE»
					«writeField(f, "this." + getterName(f) + "()")»
				«ENDIF»
			«ENDFOR»
		}
	'''

	def writeArray(Member m) {
		if (m.array.dimension > 0) {
			return writeStaticArray(m)
		} else {
			return writeDynamicArray(m)
		}
	}

	def writeStaticArray(Member m) '''
		«FOR i : 0 ..< m.array.dimension»
			«alignField(m)»
			«writeField(m, downcastIfNecessarry(m) + "this." + getterName(m) + "().get(" + i + ")")»
		«ENDFOR»
	'''

	def writeDynamicArray(Member m) '''
		{
			int arrayLength = this.«getterName(m)»().length();
			for(int i = 0; i < arrayLength; ++i) {
				«alignField(m)»
				«writeField(m, downcastIfNecessarry(m) + "this." + getterName(m) + "().get(i)")»
			}
		}
	''' 
	
	def downcastIfNecessarry(Member m) {
		if(m instanceof ComplexTypeMember) {
			return ""
		}
		return "("+native2JavaType(nativeTypeName(m))+")";
	}

	def writeField(Member m, CharSequence expr) {
		switch (m) {
			ComplexTypeMember: writeField(m as ComplexTypeMember, expr)
			IntegerMember: writeField(m as IntegerMember, expr)
			FloatMember: writeField(m as FloatMember, expr)
			StringMember: writeField(m as StringMember, expr)
			default: throw new RuntimeException("Unsupported member type: " + m)
		}
	}

	def writeField(ComplexTypeMember m, CharSequence expr) '''
		«expr».write(buf);
	'''

	def hasAtLeastOneStringMember(StructDeclaration struct) {
		for (m : struct.members) {
			if (m instanceof StringMember) {
				return true;
			}
		}
		return false;
	}

	def alignField(Member m) '''
		«IF m.align > 1»
			{
				int pos = buf.position();
				int gap = «m.align» - (pos % «m.align»);
				if(gap > 0 && gap != «m.align») {
					buf.position(pos + gap);	
				}
			}
		«ENDIF»
	'''

	def writeField(IntegerMember m, CharSequence expr) {
		switch (m.typename) {
			case "int8_t": '''buf.put((byte)«expr»);'''
			case "uint8_t": '''buf.put((byte)«expr»);'''
			case "int16_t": '''buf.putShort((short)«expr»);'''
			case "uint16_t": '''buf.putShort((short)«expr»);'''
			case "int32_t": '''buf.putInt(«expr»);'''
			case "uint32_t": '''buf.putInt(«expr»);'''
			case "int64_t": '''buf.putLong(«expr»);'''
			case "uint64_t": '''buf.putLong(«expr»);'''
		}
	}

	def writeField(FloatMember m, CharSequence expr) {
		switch (m.typename) {
			case "float": '''buf.putFloat(this.«attributeName(m)»);'''
			case "double": '''buf.putDouble(this.«attributeName(m)»);'''
		}
	}

	def writeField(StringMember m, CharSequence expr) '''
		try {
			String str = «expr»;
			byte[] encoded = str.getBytes("«encodingOf(m)»");
			«IF m.nullTerminated»
				buf.put(encoded);
				buf.put("\0".getBytes("«encodingOf(m)»"));
			«ELSE»
				int len = encoded.length;
				int pad = «dimensionOf(m)» - len;
				buf.put(encoded, 0, len);
				if(pad > 0) {
					for(int i = 0; i < pad; ++i) {
						buf.put((byte)0);	
					}
				}
			«ENDIF»
		} catch(java.io.UnsupportedEncodingException e) {
			throw new java.io.IOException(e);
		}
	'''
	
	
	def encodingOf(StringMember m) {
		if(m.encoding != null) {
			return m.encoding;
		}
		
		return "UTF-8";
	}

	def packageDeclaration(Package pkg) '''
		«IF !pkg.name.empty»
			package «pkg.name»;
		«ENDIF»
	'''

	def fields(StructDeclaration struct) '''
		«FOR f : struct.members»
			«field(f)»
		«ENDFOR»
	'''

	def getters(StructDeclaration struct) '''
		«FOR f : struct.members»
			«getter(f)»
		«ENDFOR»
	'''

	def setters(StructDeclaration struct) '''
		«FOR f : struct.members»
			«setter(f)»
		«ENDFOR»
	'''

	def field(Member m) '''
		private «native2JavaType(m)» «attributeName(m)»;
	'''

	def getter(Member m) '''
		public «native2JavaType(m)» «getterName(m)»() {
			return this.«attributeName(m)»;
		}
	'''

	def setter(Member m) '''
		public void «setterName(m)»(«native2JavaType(m)» «attributeName(m)») {
			this.«attributeName(m)» = «attributeName(m)»;
		}
	'''

	def native2JavaType(Member m) {
		val nativeType = nativeTypeName(m)
		val javaType = native2JavaType(nativeType)

		if (isArray(m)) {
			return mapArrayToType(m, javaType)
		} else {
			return javaType
		}
	}
	
	def mapArrayToType(Member m, String elementType) {
		if(m instanceof StringMember) {
			return "String"
		}
		if(elementType.equalsIgnoreCase("Byte")) {
			return "java.nio.ByteBuffer"
		}
		return "java.util.ArrayList<" + box(elementType) + ">"
	}

	def box(String type) {
		switch (type) {
			case "byte": "Byte"
			case "short": "Short"
			case "int": "Integer"
			case "long": "Long"
			case "float": "Float"
			case "double": "Double"
			case "boolean": "Boolean"
			default: type
		}
	}

	def unbox(String type) {
		switch (type) {
			case "Short": "short"
			case "Int": "int"
			case "Long": "long"
			case "Float": "float"
			case "Double": "double"
			default: type
		}
	}

	def nativeTypeName(Member m) {
		switch (m) {
			ComplexTypeMember: javaType((m as ComplexTypeMember).type)
			IntegerMember: (m as IntegerMember).typename
			FloatMember: (m as FloatMember).typename
			StringMember: (m as StringMember).typename
			default: throw new RuntimeException("Unsupported member type: " + m)
		}
	}

	def native2JavaType(String type) {
		switch (type) {
			case "uint8_t": "int"
			case "int8_t": "int"
			case "uint16_t": "int"
			case "int16_t": "int"
			case "int32_t": "int"
			case "uint32_t": "int"
			case "int64_t": "long"
			case "uint64_t": "long"
			case "char": "String"
			case "bool": "boolean"
			default: type
		}
	}

	def javaType(ComplexTypeDeclaration type) {
		val pkg = type.eContainer as Package
		if (pkg != null && !pkg.name.empty) {
			return pkg.name + "." + type.name
		}
		return type.name
	}
}
