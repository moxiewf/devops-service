import React, { Fragment, useRef, useMemo, lazy, Suspense } from 'react';
import { observer } from 'mobx-react-lite';
import isEmpty from 'lodash/isEmpty';
import classnames from 'classnames';
import Draggable from 'react-draggable';
import Sidebar from './sidebar';
import LoadingBar from '../../../components/loadingBar';
import { useMainStore } from './stores';
import { useDeploymentStore } from '../stores';
import { useResize, X_AXIS_WIDTH, X_AXIS_WIDTH_MAX } from './useResize';

import './styles/index.less';

// 实例视图
const EnvContent = lazy(() => import('./contents/environment'));
const AppContent = lazy(() => import('./contents/application'));
const IstContent = lazy(() => import('./contents/instance'));

// 资源视图
const ResourceEnvContent = lazy(() => import('./contents/resource-environment'));
const NetworkContent = lazy(() => import('./contents/network'));
const IngressContent = lazy(() => import('./contents/ingress'));
const CertContent = lazy(() => import('./contents/certificate'));
const KeyValueContent = lazy(() => import('./contents/key-value'));
const CustomContent = lazy(() => import('./contents/custom'));
const IstListContent = lazy(() => import('./contents/instance-list'));
const CustomDetail = lazy(() => import('./contents/custom-detail'));
const IngressDetail = lazy(() => import('./contents/ingress-detail'));
const CertDetail = lazy(() => import('./contents/certificate-detail'));
const ConfigMapDetail = lazy(() => import('./contents/config-detail'));
const SecretDetail = lazy(() => import('./contents/secret-detail'));
const ServiceDetail = lazy(() => import('./contents/service-detail'));

const MainView = observer(() => {
  const {
    prefixCls,
    deploymentStore: {
      getViewType,
      getSelectedMenu,
    },
    viewTypeMappings: {
      IST_VIEW_TYPE,
    },
    itemType: {
      ENV_ITEM,
      APP_ITEM,
      IST_ITEM,
      SERVICES_ITEM,
      INGRESS_ITEM,
      CERT_ITEM,
      MAP_ITEM,
      CIPHER_ITEM,
      CUSTOM_ITEM,
      SERVICES_GROUP,
      INGRESS_GROUP,
      CERT_GROUP,
      MAP_GROUP,
      CIPHER_GROUP,
      CUSTOM_GROUP,
      IST_GROUP,
    },
  } = useDeploymentStore();
  const { mainStore } = useMainStore();
  const rootRef = useRef(null);
  const content = useMemo(() => {
    const { menuType } = getSelectedMenu;
    const cmMaps = {
      [ENV_ITEM]: getViewType === IST_VIEW_TYPE ? <EnvContent /> : <ResourceEnvContent />,
      [APP_ITEM]: <AppContent />,
      [IST_ITEM]: <IstContent />,
      [SERVICES_GROUP]: <NetworkContent />,
      [INGRESS_GROUP]: <IngressContent />,
      [CERT_GROUP]: <CertContent />,
      [MAP_GROUP]: <KeyValueContent contentType={MAP_GROUP} />,
      [CIPHER_GROUP]: <KeyValueContent contentTypeg={CIPHER_GROUP} />,
      [CUSTOM_GROUP]: <CustomContent />,
      [IST_GROUP]: <IstListContent />,
      [CUSTOM_ITEM]: <CustomDetail />,
      [INGRESS_ITEM]: <IngressDetail />,
      [CERT_ITEM]: <CertDetail />,
      [MAP_ITEM]: <ConfigMapDetail />,
      [CIPHER_ITEM]: <SecretDetail />,
      [SERVICES_ITEM]: <ServiceDetail />,
    };
    return cmMaps[menuType]
      ? <Suspense fallback={<div>loading</div>}>{cmMaps[menuType]}</Suspense>
      : <div>加载中</div>;
  }, [getSelectedMenu.menuType, getViewType]);
  const {
    isDragging,
    bounds,
    resizeNav,
    draggable,
    handleUnsetDrag,
    handleStartDrag,
    handleDrag,
  } = useResize(rootRef, mainStore);

  const dragPrefixCls = `${prefixCls}-draggers`;
  const draggableClass = useMemo(() => classnames({
    [`${dragPrefixCls}-handle`]: true,
    [`${dragPrefixCls}-handle-dragged`]: isDragging,
  }), [isDragging]);

  const dragRight = resizeNav.x >= X_AXIS_WIDTH_MAX ? X_AXIS_WIDTH_MAX : bounds.width - X_AXIS_WIDTH;

  return (<div
    ref={rootRef}
    className={`${prefixCls}-wrap`}
  >
    {draggable && (
      <Fragment>
        <Draggable
          axis="x"
          position={resizeNav}
          bounds={{
            left: X_AXIS_WIDTH,
            right: dragRight,
            top: 0,
            bottom: 0,
          }}
          onStart={handleStartDrag}
          onDrag={handleDrag}
          onStop={handleUnsetDrag}
        >
          <div className={draggableClass} />
        </Draggable>
        {isDragging && <div className={`${dragPrefixCls}-blocker`} />}
      </Fragment>
    )}
    <Fragment>
      <Sidebar />
      {!isEmpty(getSelectedMenu) ? <div className={`${prefixCls}-main ${dragPrefixCls}-animate`}>
        {content}
      </div> : <LoadingBar display />}
    </Fragment>
  </div>);
});

export default MainView;