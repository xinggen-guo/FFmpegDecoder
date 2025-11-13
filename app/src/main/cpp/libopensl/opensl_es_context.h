#ifndef _MEDIA_OPENSL_ES_CONTEXT_H_
#define _MEDIA_OPENSL_ES_CONTEXT_H_

#include "opensl_es_util.h"
#include "../common/CommonTools.h"
#include <CommonTools.h>

#define LOG_TAG "OpenSLESContext"

class OpenSLESContext {
private:
	SLObjectItf engineObject;
	SLEngineItf engineEngine;
	bool isInited;
	/**
	 * Creates an OpenSL ES engine.
	 */
	SLresult createEngine() {
        SLresult result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            LOGE("slCreateEngine failed: %d", result);
        }
        return result;
	};
	/**
	 * Realize the given object. Objects needs to be
	 * realized before using them.
	 * @param object object instance.
	 */
	SLresult RealizeObject(SLObjectItf object) {
		// Realize the engine object
		return (*object)->Realize(object, SL_BOOLEAN_FALSE); // No async, blocking call
	};
	/**
	 * Gets the engine interface from the given engine object
	 * in order to create other objects from the engine.
	 */
	SLresult GetEngineInterface() {
		// Get the engine interface
		return (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
	};

	OpenSLESContext();
	void init();
	static OpenSLESContext* instance;
public:
	static OpenSLESContext* GetInstance(); //工厂方法(用来获得实例)
	virtual ~OpenSLESContext();
	SLEngineItf getEngine() {
		return engineEngine;
	};
};
#endif	//_MEDIA_OPENSL_ES_CONTEXT_H_
